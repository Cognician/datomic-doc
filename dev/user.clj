(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [cognician.datomic-doc :as dd]
            [com.stuartsierra.component :as component]
            [cognician.datomic-doc.ring :as ring]
            [figwheel-sidecar.system :as figwheel]
            [org.httpkit.server :as http]
            [ring.util.response :as response]))

(def db-uri
  "Fill in your own database uri here, or if you just want to demo,
  follow the steps at https://github.com/Datomic/mbrainz-sample#getting-the-data
  to download a data set."
  "datomic:free://localhost:4334/*")

(def config
  {::dd/datomic-uri      db-uri
   ::dd/allow-read-pred (constantly true)
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

(defn reset []
  (stop-web)
  (refresh :after 'user/start-web))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Development cljs build

(def figwheel-system
  (component/system-map
   :figwheel-system
   (figwheel/figwheel-system
    {:all-builds 
     [{:id "dev"
       :source-paths ["src"]
       :figwheel {:on-jsload "cognician.datomic-doc.client/start-client!"}
       :compiler 
       {:optimizations :none
        :output-to "resources/cognician/datomic-doc/js/main.js"
        :output-dir "target/js/cognician/datomic-doc/dev"
        :compiler-stats true
        :parallel-build true
        :source-map true
        :source-map-timestamp true
        :main 'cognician.datomic-doc.client
        :asset-path "/cognician/datomic-doc/dev"}}]
     :build-ids ["dev"]
     :figwheel-options
      {:http-server-root "."
       :server-ip "0.0.0.0"
       :server-port 4000
       :repl false}})
   :css-watcher
   (figwheel/css-watcher 
    {:watch-paths ["resources/cognician/datomic-doc"]
     :log-writer *out*})))

(def start-figwheel #(alter-var-root #'figwheel-system component/start))

(def stop-figwheel #(alter-var-root #'figwheel-system component/stop))
