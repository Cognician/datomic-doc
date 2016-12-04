(ns cognician.datomic-doc.dev-server
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [datomic.api :as d]
    [cognician.datomic-doc.ring :as ring]))

(def conn #(d/connect "datomic:dev://localhost:4334/cognician"))

(defn wrap-provide [handler]
  (fn [req]
    (@handler (assoc req :conn (conn)))))

(defn index [req]
  (when (and (= :get (:request-method req))
             (= "/" (:uri req)))
    { :body (slurp (io/resource "cognician/datomic_doc/index.html"))}))

(def api (wrap-provide #'ring/api))

(defn handler [req]
  (or (index req)
      (api req)))
