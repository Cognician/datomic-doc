(ns cognician.datomic-doc.ring
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [cognician.datomic-doc :as dd]
    [cognician.datomic-doc.transit :as transit]
    [cognician.datomic-doc.util :as util]
    [datomic.api :as d])
  (:import
    java.io.InputStream))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Options parsing

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
      (update ::dd/uri-prefix #(format "/%s/" %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Parse URI

(s/def ::dd/entity-uri
  (s/or :ident-ns  (s/cat :lookup-type #{"ident"}  :ns string? :name string?)
        :ident-n   (s/cat :lookup-type #{"ident"}              :name string?)
        :entity-ns (s/cat :lookup-type #{"entity"} :ns string? :name string? :value string?)
        :entity-n  (s/cat :lookup-type #{"entity"}             :name string? :value string?)))

(defn parse-entity-uri [uri]
  (if-let [params (seq (drop 2 (str/split uri #"/")))]
    (let [conformed (s/conform ::dd/entity-uri params)]
      (when (not= ::s/invalid conformed)
        (let [[_ {:keys [lookup-type ns name value]}] conformed
              lookup-type (keyword lookup-type)
              lookup-attr (if ns
                            (keyword ns name)
                            (keyword name))]
          {::dd/lookup-type lookup-type
           ::dd/lookup-ref  (case lookup-type
                              :ident  lookup-attr  
                              :entity [lookup-attr value])})))
    ::search))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Build context

(def pull-spec
  [:db/id
   :db/doc
   :db/ident
   {:db/valueType   [:db/ident]}
   {:db/cardinality [:db/ident]}
   {:db/unique      [:db/ident]}
   :db/index
   :db/noHistory
   :db/isComponent
   :db/fulltext])

(defn flatten-idents [m]
  (walk/postwalk (fn [item]
                   (if (and (map? item) (:db/ident item) (= 1 (count (keys item))))
                     (:db/ident item)
                     item))
                 m))

(defn pull-entity [db lookup-ref deprecated-attr]
  (-> (d/pull db (cond-> pull-spec
                   deprecated-attr (conj deprecated-attr))
                 lookup-ref)
      flatten-idents))

(defn entity-stats [db {:keys [::dd/lookup-type ::dd/lookup-ref]}]
  {::dd/created      (d/q '[:find (min ?ts) . :in $ ?e :where
                            [?e _ _ ?t true]
                            [?t :db/txInstant ?ts]]
                          (d/history db) lookup-ref)
   ::dd/last-changed (case lookup-type
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
   ::dd/datom-count  (count (case lookup-type
                              :ident
                              (seq (concat
                                    (d/datoms db :aevt lookup-ref)
                                    (d/datoms db :vaet lookup-ref)))
                              :entity
                              (seq (d/datoms db :eavt lookup-ref))))})

(s/def ::dd/context
  (s/keys :req [::dd/conn
                ::dd/db
                ::dd/annotate-tx-fn]
          :opt [::dd/read-only?
                ::dd/deprecated-attr
                ::dd/entity
                ::dd/lookup-type
                ::dd/lookup-ref]))

(defn build-context [{:keys [::dd/datomic-uri ::dd/deprecated-attr] :as options} params]
  (let [conn (d/connect datomic-uri)
        db (d/db conn)]
    (cond-> (-> options
                (select-keys [::dd/read-only?
                              ::dd/deprecated-attr
                              ::dd/annotate-tx-fn]) 
                (merge {::dd/conn conn
                        ::dd/db db}))
      (not= ::search params) 
      (as-> context
            (-> context
                (merge params)
                (assoc ::dd/entity (pull-entity db (::dd/lookup-ref params) 
                                                deprecated-attr))
                (update ::dd/entity merge (entity-stats db params)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn search [context]
  ["search" context])

(defn editor [context]
  ["editor" context])

(defn commit! [context payload]
  ["commit!" context payload])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Request handlers

(defn read-body [^java.io.InputStream body]
  (when (some? body)
    (.reset body) ;; resets org.httpkit.BytesInputStream to the beginning
    (transit/read-transit body)))

(defn handler [{:keys [request-method uri body ::options] :as req}]
  (let [{:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options]
    (when (str/starts-with? uri uri-prefix)
      (or (when (or (allow-write-pred req)
                    (allow-read-pred req))
            (if-let [params (parse-entity-uri uri)]
              (let [search? (= ::search params)
                    context (build-context (assoc options ::dd/read-only? 
                                                  (not (allow-write-pred req)))
                                           params)]
                (case request-method
                  :get  (if search?
                          (search context)
                          (editor context))
                  :post (when-not search?
                          (commit! context (read-body body)))))
              {:status  404
               :headers {"Content-Type" "text/plain; charset=utf-8"}
               :body    "Not found"}))
          {:status  403
           :headers {"Content-Type" "text/plain; charset=utf-8"}
           :body    "Access denied"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Ring Middleware

(def dd-api (transit/wrap-transit handler))

(defn wrap-datomic-doc [handler options]
  (let [options (prepare-options options)]
    (fn [req]
      (or (dd-api (assoc req ::options options))
          (handler req)))))

(comment
  (def db-uri "datomic:mem://test")
  (def conn (d/connect db-uri))
 
  (d/delete-database db-uri)
  (d/create-database db-uri)

  @(d/transact conn
               [{:db/ident       :user/email
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one
                 :db/unique      :db.unique/identity}
                {:db/ident :user/status
                 :db/valueType :db.type/ref
                 :db/cardinality :db.cardinality/one}
                [:db/add (d/tempid :db.part/user) :db/ident :status/active]])

  @(d/transact conn
               [{:db/id (d/tempid :db.part/user) 
                 :user/email "test@example.com"
                 :user/status :status/active}])

  (-> (handler {::options (prepare-options {::dd/datomic-uri db-uri
                                            ::dd/allow-write-pred (constantly true)})
                :uri "/dd/ident/db/doc"
                :body (-> "payload"
                          (.getBytes "UTF-8")
                          (java.io.ByteArrayInputStream.))
                :request-method :get})
      (update 1 dissoc ::dd/db))
  _)
