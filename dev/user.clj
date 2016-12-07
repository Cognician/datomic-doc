(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.repl :refer [refresh]]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.ring :as ring]
            [datomic.api :as d]
            [org.httpkit.server :as http]
            [ring.middleware.resource :as resource]))

(def db-uri "datomic:free://localhost:4334/cognician")
(def conn (partial d/connect db-uri))
(def db (comp d/db conn))

(defn index [req]
  (when (and (= :get (:request-method req))
             (= "/" (:uri req)))
    {:body (slurp (io/resource "index.html"))}))

(def handler
  (-> index
      (ring/wrap-datomic-doc 
       {::dd/datomic-uri db-uri
        ::dd/allow-write-pred (constantly true)
        ::dd/deprecated-attr :cognician/deprecated})
      (resource/wrap-resource ".")))

(defonce web-process (atom nil))

(defn start-web []
  (reset! web-process (http/run-server handler {:port 8080})))

(defn stop-web []
  (when-let [stop-fn @web-process]
    (stop-fn))
  (reset! web-process nil))

(defn reset []
  (stop-web)
  (refresh :after 'user/start-web))
