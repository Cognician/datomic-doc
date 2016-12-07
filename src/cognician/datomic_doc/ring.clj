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
  (-> (d/pull db (cond-> pull-spec
                   deprecated-attr (conj deprecated-attr))
              lookup-ref)
      util/flatten-idents))

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
  (let [ident-type (and (= lookup-type :ident)
                        (classify-ident db lookup-ref))]
    (cond->
      {:created      (p :created 
                        (tx->txInstant db
                                       (d/q '[:find (min ?t) . :in $ ?e :where
                                              [?e _ _ ?t true]]
                                            (d/history db) lookup-ref)))
       :last-changed (tx->txInstant db
                                    (case lookup-type
                                      :ident
                                      (case ident-type
                                        :partition nil
                                        :function
                                        (p :last-changed-function
                                           (d/q '[:find (max ?t) . :in $ ?e :where
                                                  [?e _ _ ?t]]
                                                db lookup-ref))
                                        :schema
                                        (p :last-changed-schema
                                           (d/q '[:find (max ?t) . :in $ ?e :where
                                                  [_ ?e _ ?t]]
                                                db lookup-ref))
                                        :enum
                                        (p :last-changed-enum
                                           (d/q '[:find (max ?t) . :in $ ?e :where
                                                  [_ _ ?e ?t]]
                                                db lookup-ref)))
                                      :entity
                                      (p :last-changed-entity
                                         (d/q '[:find (max ?t) . :in $ ?e :where
                                                [?e _ _ ?t]]
                                              db lookup-ref))))
       :datom-count  (count (seq (case lookup-type
                                   :ident
                                   (case ident-type
                                     (:function :partition) []
                                     :schema
                                     (p :datom-count-schema
                                        (doall (seq (d/datoms db :aevt lookup-ref))))
                                     :enum
                                     (p :datom-count-enum
                                        (doall (seq (d/datoms db :vaet lookup-ref)))))
                                   :entity
                                   (p :datom-count-entity
                                      (doall (seq (d/datoms db :eavt lookup-ref)))))))}
      ident-type (assoc :ident-type ident-type))))

(comment
  (tufte/add-basic-println-handler! {})
  
  (profile {} (entity-stats (user/db) :ident :find-or-create-tag))
  (profile {} (entity-stats (user/db) :ident :cognician))
  (profile {} (entity-stats (user/db) :ident :user/status))
  (profile {} (entity-stats (user/db) :ident :status/active))
  (profile {} (entity-stats (user/db) :ident :navigation-event/url))
  (profile {} (entity-stats (user/db) :ident :chat/event))
  (profile {} (entity-stats (user/db) :ident :chat.event.type/change-response))
  
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

(defn client-options [context]
  (select-keys context [::dd/uri-prefix]))

(defn datomic-schema? [attr]
  (let [ns (str (namespace attr))]
    (or (string/starts-with? ns "db")
        (string/starts-with? ns "fressian"))))

(defn all-idents-as-datoms [db]
  (into [] (comp (remove (comp datomic-schema? :v))
                 (map (fn [[e _ v]] [e :db/ident v])))
        (d/datoms db :aevt :db/ident)))

(defn search [{:keys [::dd/context] :as request}]
  (layout 
   (client-component 
    "search"
    {:options (-> context :options client-options)
     :db {:schema {:db/ident {:db/unique :db.unique/identity}}
          :datoms (all-idents-as-datoms (:db context))}})))

(defn editor [{:keys [::dd/context] :as request}]
  (layout 
   (client-component 
    "editor"
    (merge {:options (-> context :options client-options)}
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
          lookup-attr (if ns
                        (keyword ns name)
                        (keyword name))
          lookup-ref  (case lookup-type
                        :ident  lookup-attr
                        :entity [lookup-attr value])
          db          (get-in request [::dd/context :db])
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
            ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" :value]
            ["/" [#"entity" :lookup-type]         "/" :name "/" :value]}
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
