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

(comment
  
  @(d/transact (conn) [{:db/ident              :cognician/deprecated
                        :db/valueType          :db.type/boolean
                        :db/cardinality        :db.cardinality/one
                        :db/id                 #db/id[:db.part/db]
                        :db.install/_attribute :db.part/db}])
  
  @(d/transact (conn) (concat
                       (for [s [:meta/description
                                :meta/icon
                                :meta/message-templates
                                :meta/no-icon-border?
                                :meta/price
                                :meta/slug
                                :meta/title
                                :meta/vatable
                                :transaction/responsible-user-uuid
                                :transaction/user-uuid
                                :transaction/corrected?
                                :semaphore.message-transport/envisionme
                                :semaphore.message-transport/mad-mimi
                                :semaphore.message-transport/nojoshmo
                                :semaphore.message-transport/postmark]]
                         [:db/add s :cognician/deprecated true])
                       (for [s (d/q '[:find [?e ...] :in $ [?ns ...] :where
                                      [?e :db/ident ?i]
                                      [(namespace ?i) ?ns]]
                                    (db) ["address"
                                          "aggregate"
                                          "bundle-statistics"
                                          "contact"
                                          "currency"
                                          "demo-template"
                                          "fuse"
                                          "fusebox"
                                          "hex"
                                          "invite"
                                          "legacy-resource-statistics"
                                          "license"
                                          "list"
                                          "memento"
                                          "memento.tag"
                                          "message"
                                          "message-template"
                                          "message.trigger"
                                          "order"
                                          "package"
                                          "promo-code"
                                          "promo-code.value-type"
                                          "repo"
                                          "resource-statistics"
                                          "schedule"
                                          "searchable"
                                          "semaphore-template"
                                          "setting"])]
                         [:db/add s :cognician/deprecated true])))
  
  _)

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
