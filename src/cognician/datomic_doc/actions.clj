(ns cognician.datomic-doc.actions
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic]
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
      (string/replace "#js-to-load#" (str "/" asset-prefix "js/main.min.js"))))

(def template (memoize template*))

(defn dev-template [name]
  (-> (resource-file-contents name)
      (string/replace "#js-to-load#" (str "/" asset-prefix "js/main.js"))))

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

(defn routing-component [{:keys [options route route-params read-only?]}]
  (client-component options "routing"
                    (cond-> {:uri-prefix          (::dd/uri-prefix options)
                             :multiple-databases? (::dd/multiple-databases? options)
                             :route               route}
                      route-params (assoc :route-params route-params)
                      read-only? (assoc :read-only? read-only?))))

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

(defn detail [{:keys [options entity] :as request}]
  (layout "index" options
          (routing-component request)
          (client-component options "detail"
                            (cond-> (select-keys entity [:lookup-type :lookup-ref
                                                         :entity :entity-stats])
                              true (update :entity dissoc :db/doc)))))

(defn edit [{:keys [options read-only?] :as request}]
  (layout (if read-only? "mdp-view" "mdp-edit") options
          (routing-component request)))

(defn doc [request]
  (-> (or (get-in request [:entity :entity :db/doc]) "")
      response/response
      (response/content-type "text/plain; charset=UTF-8")))

(defn save [{:keys [options read-only? db-uri entity body] :as request}]
  (if read-only?
    access-denied-response
    (let [annotate-tx-fn (::dd/annotate-tx-fn options)]
      (-> (datomic/save-db-doc! db-uri entity body
                                (cond->> {:db/id (d/tempid :db.part/tx)}
                                  annotate-tx-fn (annotate-tx-fn request)))
          pr-str
          response/response))))
