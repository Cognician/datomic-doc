(ns cognician.datomic-doc.service
  (:require [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.ring :as ring]
            [org.httpkit.server :as http]
            [ring.util.response :as response]))

(def db-uri
  "Fill in your own database uri here, or if you just want to demo,
  follow the steps at https://github.com/Datomic/mbrainz-sample#getting-the-data
  to download a data set."
  "datomic:free://localhost:4334/*")

(def config
  {::dd/datomic-uri      db-uri
   ::dd/allow-read-pred  (constantly true)
   ::dd/allow-write-pred (constantly true)
   ::dd/deprecated-attr  :cognician/deprecated
   ::dd/dev-mode?        true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Web server

(def handler
  (ring/wrap-datomic-doc #(response/response (pr-str %)) config))

(defonce server (atom nil))

(defn start-web []
  (reset! server (http/run-server handler {:port 8080})))

(defn stop-web []
  (when-let [stop-fn @server]
    (stop-fn))
  (reset! server nil))
