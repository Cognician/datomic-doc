(ns cognician.datomic-doc.ring
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]
    [datomic.api :as d]
    [cognician.datomic-doc :as dd]
    [cognician.datomic-doc.transit :as transit]
    [cognician.datomic-doc.util :as util])
  (:import
    [java.io InputStream]))

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

#_ (prepare-options {::dd/datomic-uri "datomic:mem://test"
                     ::dd/allow-write-pred (constantly true)})

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

(comment
  (parse-entity-uri "/dd")
  (parse-entity-uri "/dd/")
  (parse-entity-uri "/dd/ident/foo")
  (parse-entity-uri "/dd/ident/foo/bar")
  (parse-entity-uri "/dd/entity/foo/bar")
  (parse-entity-uri "/dd/entity/foo/bar/baz"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn search [context]
  "search")

(defn editor [context]
  ["editor" (::dd/entity context)])

(defn commit! [context payload]
  ["commit!" (::dd/entity context) payload])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Build context

(defn build-context [options params]
  (let [conn (d/connect (::dd/datomic-uri options))
        db (d/db conn)]
    (cond-> (-> options
                (select-keys [::dd/deprecated-attr
                              ::dd/annotate-tx-fn]) 
                (merge {::dd/conn conn
                        ::dd/db db}))
      (not= ::search params) 
      (as-> context
            (-> context 
                (merge params)
                (assoc ::dd/entity 
                       (d/entity db (::dd/lookup-ref params))))))))

#_ (d/create-database "datomic:mem://test")
    
#_ (build-context (prepare-options {::dd/datomic-uri "datomic:mem://test"
                                    ::dd/allow-write-pred (constantly true)})
                  (parse-entity-uri "/dd/ident/db/doc"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Request handlers

(defn read-body [^InputStream body]
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
               
#_ (handler {::options (prepare-options {::dd/datomic-uri "datomic:mem://test"
                                         ::dd/allow-write-pred (constantly true)})
             :uri "/dd/ident/db/doc"
             :body (-> "payload"
                       (.getBytes "UTF-8")
                       (java.io.ByteArrayInputStream.))
             :request-method :post})

(def dd-api (transit/wrap-transit handler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Ring Middleware

(defn wrap-datomic-doc [handler options]
  (let [options (prepare-options options)]
    (fn [req]
      (or (dd-api (assoc req ::options options))
          (handler req)))))
