(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.ring :as ring]
            [org.httpkit.server :as http]
            [ring.middleware.resource :as resource]))

(defn index [req]
  (when (and (= :get (:request-method req))
             (= "/" (:uri req)))
    {:body (slurp (io/resource "index.html"))}))

(def handler
  (-> index
      (ring/wrap-datomic-doc 
       {::dd/datomic-uri "datomic:free://localhost:4334/cognician"
        ::dd/allow-write-pred (constantly true)})
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
