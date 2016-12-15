(ns cognician.datomic-doc.views
  (:require [bidi.bidi :as bidi]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic :refer [as-conn as-db]]
            [ring.util.response :as response]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(def access-denied-response
  {:status 403
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "Access denied"})

(def asset-prefix "cognician/datomic-doc/")

(defn layout [template-name js-to-load & content]
  (-> (str asset-prefix template-name ".html")
      io/resource
      slurp
      (string/replace "#content#" (apply str content))
      (string/replace "#js-to-load#" (str "/" asset-prefix js-to-load))
      response/response))

(def component-template
  "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>")

(def client-component (partial format component-template))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn database-list [{{:keys [::dd/js-to-load ::dd/datomic-uris] :as context} 
                      ::dd/context}] 
  (->> (client-component
        "database-list"
        {:uri-prefix (::dd/uri-prefix context)
         :databases datomic-uris})
       (layout "index" js-to-load)))

(defn search [{{:keys [::dd/deprecated-attr ::dd/js-to-load 
                       ::dd/multiple-databases? ::dd/uri-prefix]} ::dd/context
               db-uri :db-uri
               uri :uri}] 
  (->> (client-component
        "search"
        (cond-> {:uri-prefix uri
                 :db {:schema {:db/ident {:db/unique :db.unique/identity}}
                      :datoms (datomic/all-idents-as-datoms (as-db db-uri) deprecated-attr)}}
          multiple-databases? (assoc :databases-uri uri-prefix)))
       (layout "index" js-to-load)))

(defn detail [{{:keys [::dd/js-to-load read-only?] :as context} ::dd/context
               uri :uri}]
  (->> (client-component
        "detail"
        (merge {:search-uri-prefix (string/replace uri #"/(ident|entity).*" "")
                :read-only? read-only?}
               (select-keys context [:lookup-type :lookup-ref :entity :entity-stats :uri])))
       (layout "index" js-to-load)))

(defn edit [{{:keys [read-only?]} ::dd/context :as request}]
  (if read-only?
    access-denied-response
    (layout "mdp" (get-in request [::dd/context :entity :db/doc]))))
