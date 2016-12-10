(ns cognician.datomic-doc.views
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic]
            [ring.util.response :as response]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn layout [template-name & content]
  (-> (str template-name ".html")
      io/resource
      slurp
      (string/replace "#content#" (apply str content))
      response/response))

(def component-template
  "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>")

(def client-component (partial format component-template))

(defn client-options [options]
  (select-keys options [::dd/uri-prefix]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defn search [{{:keys [db options] :as context} ::dd/context :as request}]
  (->> (client-component
        "search"
        {:options (client-options options)
         :db
         {:schema {:db/ident {:db/unique :db.unique/identity}}
          :datoms (datomic/all-idents-as-datoms db (::dd/deprecated-attr options))}})
       (layout "index")))

(defn detail [{{:keys [options] :as context} ::dd/context :as request}]
  (->> (client-component
        "detail"
        (merge {:options (client-options options)}
               (select-keys context 
                            [:lookup-type :lookup-ref :entity :entity-stats :uri])))
       (layout "index")))

(defn edit [request]
  (layout "mdp" (get-in request [::dd/context :entity :db/doc])))
