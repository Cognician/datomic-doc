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

(defn read-body [^InputStream body]
  (when (some? body)
    (.reset body) ;; resets org.httpkit.BytesInputStream to the beginning
    (transit/read-transit body)))

(def access-denied
  { :status  403
    :headers { "Content-Type" "text/plain; charset=utf-8"}
    :body    "Access denied"})

(s/def ::dd/entity-uri
  (s/or ::ident-ns  (s/cat :dd/ident-type #{"ident"}   ::dd/ns string? ::dd/name string?)
        ::ident-n   (s/cat :dd/ident-type #{"ident"}                   ::dd/name string?)
        ::entity-ns (s/cat :dd/entity-type #{"entity"} ::dd/ns string? ::dd/name string? ::dd/value string?)
        ::entity-n  (s/cat :dd/entity-type #{"entity"}                 ::dd/name string? ::dd/value string?)))

(defn parse-entity-uri [uri]
  (if-let [params (seq (drop 2 (str/split uri #"/")))]
    (util/conform! ::dd/entity-uri params)
    [::search]))

(comment
  (parse-entity-uri "/dd")
  (parse-entity-uri "/dd/")
  (parse-entity-uri "/dd/ident/foo")
  (parse-entity-uri "/dd/ident/foo/bar")
  (parse-entity-uri "/dd/entity/foo/bar")
  (parse-entity-uri "/dd/entity/foo/bar/baz"))

(declare search editor commit!)

(defn handler [{:keys [request-method uri conn body ::options] :as req}]
  (let [{:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options]
    (when (and (str/starts-with? uri uri-prefix))
      (or (when (or (allow-write-pred req)
                    (allow-read-pred req))
            (let [[action params] (util/conform! ::dd/entity-uri (str/split uri #"/"))
                  search? (= ::search action)
                  context (-> options
                              (select-keys [::dd/deprecated-attr
                                            ::dd/annotate-tx-fn]) 
                              (merge (cond-> {:db             (d/db conn)
                                              ::dd/read-only? (not (allow-write-pred req))}
                                       params (merge params))))]
              (case request-method
                :get
                (if search?
                  (search context)
                  (editor context))
                :post
                (when-not search?
                  (commit! context (read-body body))))))
          access-denied))))

(def dd-api (transit/wrap-transit handler))

(s/def ::dd/datomic-uri string?)
(s/def ::dd/allow-read-pred fn?)
(s/def ::dd/allow-write-pred fn?)
(s/def ::dd/deprecated-attr keyword?)
(s/def ::dd/annotate-tx-fn fn?)
(s/def ::dd/uri-prefix string?)

(s/def ::dd/config (s/keys :req [::dd/datomic-uri]
                           :opt [::dd/allow-read-pred
                                 ::dd/allow-write-pred
                                 ::dd/deprecated-attr
                                 ::dd/annotate-tx-fn
                                 ::dd/uri-prefix]))

(defn prepare-options [options]
  (-> (util/conform! ::dd/config options)
      (update ::dd/uri-prefix #(format "/%s/" (or % "dd")))))

#_ (prepare-options {::dd/datomic-uri "datomic:mem://test"
                     ::dd/allow-write-pred (constantly true)})
#_ (ex-data *e)

(defn wrap-datomic-doc [handler options]
  (let [options (prepare-options options)]
    (fn [req]
      (or (dd-api (assoc req ::options options))
          (handler req)))))
