(ns cognician.datomic-doc.actions
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic :refer [as-conn]]
            [datomic.api :as d]
            [ring.util.response :as response]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(def access-denied-response
  {:status  403
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body    "Access denied"})

(def asset-prefix "cognician/datomic-doc/")

(def resource-file-contents (comp slurp io/resource))

(defn template* [name]
  (-> (resource-file-contents name)
      (string/replace "#js-to-load#" (str "/" asset-prefix "main.min.js"))))

(def template (memoize template*))

(defn dev-template [name]
  (-> (resource-file-contents name)
      (string/replace "#js-to-load#" (str "/" asset-prefix "main.js"))))

(defn layout [template-name {:keys [::dd/dev-mode?]} & content]
  (let [template-name (str asset-prefix "/" template-name ".html")]
    (-> (if dev-mode?
          (dev-template template-name)
          (template template-name))
        (string/replace "#content#" (apply str content))
        response/response)))

(def component-template
  "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>")

(defn client-component [{:keys [::dd/dev-mode?]} name payload]
  (format component-template name
          (if dev-mode?
            (format "\n\n%s\n\n" (with-out-str (pprint/pprint payload)))
            payload)))

(defn routing-component [{:keys [options routes route route-params]}]
  (client-component options "routing"
                    (cond-> {:routes routes
                             :route route}
                      route-params (assoc :route-params route-params))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn database-list [{:keys [options] :as request}]
  (layout "index" options
          (routing-component request)
          (client-component options "database-list"
                            {:databases (get-in request [:options ::dd/datomic-uris])})))

(defn search [{:keys [options db-uri] :as request}]
  (layout "index" options
          (routing-component request)
          (client-component options "search" (datomic/datascript-db db-uri options))))

(defn detail [{:keys [options entity read-only?] :as request}]
  (layout "index" options
          (routing-component request)
          (client-component options "detail"
                            (cond-> (select-keys entity [:lookup-type :lookup-ref
                                                         :entity :entity-stats])
                              read-only? (assoc :read-only? read-only?)))))

(defn edit [{:keys [options entity read-only?] :as request}]
  (if read-only?
    access-denied-response
    (layout "mdp" options
            (routing-component request)
            (format "<script>var editor_content = '%s';</script>"
                    (get-in entity [:entity :db/doc])))))

(defn save! [{:keys [options db-uri entity body]
              :as request}]
  (let [{:keys [lookup-ref entity]} entity
        annotate-tx-fn (::dd/annotate-tx-fn options)
        tx (cond-> [[:db.fn/cas lookup-ref :db/doc (:db/doc entity) (slurp body)]]
             annotate-tx-fn
             (conj (annotate-tx-fn request {:db/id (d/tempid :db.part/tx)})))]
    (try
      @(d/transact (as-conn db-uri) tx)
      :ok
      (catch Throwable e
        (or (-> e .getCause ex-data :db/error) :error)))))

(defn save [{:keys [read-only?] :as request}]
  (if read-only?
    access-denied-response
    (-> (save! request)
        pr-str
        response/response)))
