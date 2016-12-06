(ns cognician.datomic-doc.ring
  (:require
   [bidi.ring :as bidi-ring]
   [clojure.pprint :as pprint]
   [clojure.spec :as s]
   [clojure.string :as str]
   [cognician.datomic-doc :as dd]
   [cognician.datomic-doc.transit :as transit]
   [cognician.datomic-doc.util :as util]
   [datomic.api :as d]
   [ring.util.response :as response]))

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

(defn entity-stats [db lookup-type lookup-ref]
  {:created      (d/q '[:find (min ?ts) . :in $ ?e :where
                        [?e _ _ ?t true]
                        [?t :db/txInstant ?ts]]
                      (d/history db) lookup-ref)
   :last-changed (case lookup-type
                   :ident
                   (d/q '[:find (max ?ts) . :in $ ?e :where
                          (or-join [?e ?t]
                            [_ ?e _ ?t]
                            (and [?i :db/ident ?e]
                                 [_ _ ?i ?t]))
                          [?t :db/txInstant ?ts]]
                        db lookup-ref)
                   :entity
                   (d/q '[:find (max ?ts) . :in $ ?e :where
                          [?e _ _ ?t]
                          [?t :db/txInstant ?ts]]
                        db lookup-ref))
   :datom-count  (count (case lookup-type
                          :ident
                          (seq (concat
                                (d/datoms db :aevt lookup-ref)
                                (d/datoms db :vaet lookup-ref)))
                          :entity
                          (seq (d/datoms db :eavt lookup-ref))))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn pprint-pre [val]
  (format "<pre style='white-space: pre-wrap;'>%s</pre>"
          (with-out-str (pprint/pprint val))))

(defn search [request]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "search:<br>" (pprint-pre (::dd/context request)))})

(defn editor [request]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "editor:<br>" (pprint-pre (::dd/context request)))})

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
      (or (when (str/starts-with? (:uri request) uri-prefix)
            (if (or (allow-write-pred request)
                    (allow-read-pred request))
              (dd-handler (assoc request ::dd/context
                                 {:read-only? (not (allow-write-pred request))}))
              {:status  403
               :headers {"Content-Type" "text/plain; charset=utf-8"}
               :body    "Access denied"}))
          (handler request)))))
