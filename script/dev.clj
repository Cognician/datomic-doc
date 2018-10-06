(require '[clojure.tools.nrepl.server :as nrepl.server]
         '[refactor-nrepl.middleware :refer [wrap-refactor]])

(defonce *nrepl-server (atom nil))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-nrepl-server! [port & [wrap]]
  (let [server (nrepl.server/start-server :port port
                                          :handler (cond-> (nrepl-handler)
                                                     wrap wrap))]
    (prn "Started nREPL server on" port)
    (reset! *nrepl-server server)
    server))

(defn stop-nrepl-server! []
  (nrepl.server/stop-server @*nrepl-server))

(def port 7870)

(spit ".nrepl-port" (str port))

(start-nrepl-server! port wrap-refactor)

(.addShutdownHook (Runtime/getRuntime)
                  (Thread. #(clojure.java.io/delete-file ".nrepl-port")))


;; Figwheel

(require '[figwheel.main :refer [start]])

(start "dev")
