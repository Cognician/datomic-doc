(ns cognician.datomic-doc.ring
  (:require
   [bidi.ring :as bidi-ring]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.spec :as s]
   [clojure.string :as string]
   [cognician.datomic-doc :as dd]
   [cognician.datomic-doc.transit :as transit]
   [cognician.datomic-doc.util :as util]
   [datomic.api :as d]
   [ring.util.response :as response]
   [taoensso.tufte :as tufte :refer [defnp profile p]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Options

(s/def ::dd/datomic-uri string?)
(s/def ::dd/uri-prefix string?)
(s/def ::dd/allow-write-pred fn?)
(s/def ::dd/allow-read-pred fn?)
(s/def ::dd/deprecated-attr keyword?)
(s/def ::dd/annotate-tx-fn fn?)
(s/def ::dd/config (s/keys :req [::dd/datomic-uri]
                           :opt [::dd/uri-prefix
                                 ::dd/allow-write-pred
                                 ::dd/allow-read-pred
                                 ::dd/deprecated-attr
                                 ::dd/annotate-tx-fn]))

(def default-options
  {::dd/uri-prefix       "dd"
   ::dd/allow-write-pred (constantly false)
   ::dd/allow-read-pred  (constantly false)
   ::dd/annotate-tx-fn   identity})

(defn prepare-options [options]
  (-> default-options
      (merge (util/conform! ::dd/config options))
      (update ::dd/uri-prefix #(str "/" %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fetch Entity

(def pull-spec
  [:db/id
   :db/doc
   :db/ident
   {:db/valueType [:db/ident]}
   {:db/cardinality [:db/ident]}
   {:db/unique [:db/ident]}
   :db/index
   :db/noHistory
   :db/isComponent
   :db/fulltext])

(defn pull-entity [db lookup-ref deprecated-attr]
  (let [entity (-> (d/pull db (cond-> pull-spec
                                deprecated-attr (conj deprecated-attr))
                           lookup-ref)
                   util/flatten-idents)]
    (cond-> entity
      (get entity deprecated-attr) (assoc :deprecated? true))))

(defn classify-ident [db lookup-ref]
  (let [entity (d/entity db lookup-ref)]
    (cond
      (:db/valueType entity)          :schema
      (:db.install/_partition entity) :partition
      (:db/fn entity)                 :function
      :else                           :enum)))

(defn tx->txInstant [db tx]
  (->> tx (d/entity db) :db/txInstant))

(defn entity-stats [db lookup-type lookup-ref]
  (let [history-db (d/history db)
        index (case lookup-type
                :partition          nil
                :schema             :aevt
                :enum               :vaet
                (:function :entity) :eavt)]
    (cond-> {:created 
             (p :created
                (->> (d/datoms history-db :eavt lookup-ref)
                     (transduce (map :tx) min Long/MAX_VALUE)
                     (tx->txInstant db)))}
      (contains? #{:schema :enum :entity :function} lookup-type)
      (assoc :last-touched 
             (p :last-touched
                (->> (d/datoms history-db index lookup-ref)
                     (transduce (map :tx) max 0)
                     (tx->txInstant db))))
      (contains? #{:schema :enum :entity} lookup-type)
      (assoc :datom-count 
             (p :datom-count
                (-> (seq (d/datoms db index lookup-ref))
                    seq
                    count))))))

(comment
  (tufte/add-basic-println-handler! {})
  
  (entity-stats (user/db) :schema :meta/no-icon-border?)
  
  (profile {} (entity-stats (user/db) :function :find-or-create-tag))
  (profile {} (entity-stats (user/db) :partition :cognician))
  (profile {} (entity-stats (user/db) :schema :user/status))
  (profile {} (entity-stats (user/db) :enum :status/active))
  (profile {} (entity-stats (user/db) :schema :navigation-event/url))
  (profile {} (entity-stats (user/db) :schema :chat/event))
  (profile {} (entity-stats (user/db) :enum :chat.event.type/change-response))
  (profile {} (entity-stats (user/db) :entity [:user/email "barry@cognician.com"]))
  _)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn pprint-pre [val]
  (format "<pre style='white-space: pre-wrap;'>%s</pre>"
          (with-out-str (pprint/pprint val))))

(defn layout [content]
  (-> "index.html"
      io/resource
      slurp
      (string/replace "#content#" content)
      response/response))

(defn client-component [name data]
  (format "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>"
          name data))

(defn client-options [options]
  (select-keys options [::dd/uri-prefix]))

(defn datomic-schema? [attr]
  (let [ns (str (namespace attr))]
    (or (string/starts-with? ns "db")
        (string/starts-with? ns "fressian"))))

(defn all-idents-as-datoms [db deprecated-attr]
  (into [] (comp (remove (comp datomic-schema? :v))
                 (mapcat (fn [[e _ v]]
                           (let [entity (d/entity db e)]
                             (cond-> [[e :db/ident v]
                                      [e :ident-type (classify-ident db v)]]
                               (get entity deprecated-attr) (conj [e :deprecated? true]))))))
        (d/datoms db :aevt :db/ident)))

(defn search [{{:keys [db options] :as context} ::dd/context :as request}]
  (layout
   (client-component
    "search"
    {:options (client-options options)
     :db      {:schema {:db/ident {:db/unique :db.unique/identity}}
               :datoms (all-idents-as-datoms db (::dd/deprecated-attr options))}})))

(defn editor [{{:keys [options] :as context} ::dd/context :as request}]
  (layout
   (client-component
    "editor"
    (merge {:options (client-options options)}
           (select-keys context [:lookup-type :lookup-ref :entity :entity-stats])))))

(defn commit! [request]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "commit!:<br>"
                 (pprint-pre (::dd/context request))
                 (pprint-pre (transit/read-transit-body (:body request))))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Ring Middleware

(defn wrap-with-context [options handler]
  (fn [request]
    (-> (update request ::dd/context merge
                (let [conn (d/connect (::dd/datomic-uri options))]
                  {:conn    conn
                   :db      (d/db conn)
                   :options (select-keys options [::dd/uri-prefix
                                                  ::dd/deprecated-attr
                                                  ::dd/annotate-tx-fn])}))
        handler)))

(defn wrap-with-entity [options handler]
  (fn [{{:keys [lookup-type ns name value]} :route-params :as request}]
    (let [lookup-type (keyword lookup-type)
          name (string/replace name "__Q" "?")
          lookup-attr (if ns
                        (keyword ns name)
                        (keyword name))
          lookup-ref  (case lookup-type
                        :ident  lookup-attr
                        :entity [lookup-attr value])
          db          (get-in request [::dd/context :db])
          lookup-type (case lookup-type
                        :ident  (classify-ident db lookup-ref)
                        :entity :entity)
          entity-map  (pull-entity db lookup-ref (::dd/deprecated-attr options))]
      (if (= entity-map {:db/id nil})
        (response/not-found "That entity does not exist.")
        (-> (update request ::dd/context merge
                    {:lookup-type  lookup-type
                     :lookup-ref   lookup-ref
                     :entity       entity-map
                     :entity-stats (entity-stats db lookup-type lookup-ref)})
            handler)))))

(defn make-routes [uri-prefix options]
  [uri-prefix
   {:get {"" search
          #{["/" [#"ident"  :lookup-type] "/" :ns "/" :name]
            ["/" [#"ident"  :lookup-type]         "/" :name]
            ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" [#"[^/]+" :value]]
            ["/" [#"entity" :lookup-type]         "/" :name "/" [#"[^/]+" :value]]}
          (bidi-ring/wrap-middleware editor (partial wrap-with-entity options))}}])

(defn wrap-datomic-doc [handler options]
  (let [options (prepare-options options)
        {:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options
        dd-handler (wrap-with-context options
                                      (-> uri-prefix
                                          (make-routes options)
                                          bidi-ring/make-handler))]
    (fn [request]
      (or (when (string/starts-with? (:uri request) uri-prefix)
            (if (or (allow-write-pred request)
                    (allow-read-pred request))
              (dd-handler (assoc request ::dd/context
                                 {:read-only? (not (allow-write-pred request))}))
              {:status  403
               :headers {"Content-Type" "text/plain; charset=utf-8"}
               :body    "Access denied"}))
          (handler request)))))
