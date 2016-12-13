(ns cognician.datomic-doc.views
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic :refer [as-conn as-db]]
            [ring.util.response :as response]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn layout [template-name js-to-load & content]
  (-> (str "cognician/datomic-doc/" template-name ".html")
      io/resource
      slurp
      (string/replace "#content#" (apply str content))
      (string/replace "#js-to-load#" js-to-load)
      response/response))

(def component-template
  "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>")

(def client-component (partial format component-template))

(defn client-options [options]
  (select-keys options [::dd/uri-prefix]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn search [{{:keys [::dd/deprecated-attr ::dd/js-to-load] :as context} ::dd/context
               db-uri :db-uri}] 
  (->> (client-component
        "search"
        {:options (client-options context)
         :db {:schema {:db/ident {:db/unique :db.unique/identity}}
              :datoms (datomic/all-idents-as-datoms (as-db db-uri) deprecated-attr)}})
       (layout "index" js-to-load)))

(defn detail [{{:keys [::dd/js-to-load] :as context} ::dd/context}]
  (->> (client-component
        "detail"
        (merge {:options (client-options context)}
               (select-keys context [:lookup-type :lookup-ref :entity :entity-stats :uri])))
       (layout "index" js-to-load)))

(defn edit [request]
  (layout "mdp" (get-in request [::dd/context :entity :db/doc])))
