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

(comment
  (d/attribute (db) :db/ident)
  
  (defn datomic-schema? [attr]
    (let [ns (str (namespace attr))]
      (or (string/starts-with? ns "db")
          (string/starts-with? ns "fressian"))))
  
  (spit "db.edn"
   (pr-str {:schema {:db/ident {}}
            :datoms (into [] (comp (remove (comp datomic-schema? (partial d/ident (db)) :v))
                                   (map (fn [[e _ v]] [e :db/ident v])))
                          (d/datoms (db) :aevt :db/ident))}))
  
  (db/datascript-db (d/db (d/connect db-uri))))
