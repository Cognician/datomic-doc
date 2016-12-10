(ns cognician.datomic-doc.views
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic]
            [cognician.datomic-doc.transit :as transit]
            [ring.util.response :as response]))

(defn layout [content]
  (-> "index.html"
      io/resource
      slurp
      (string/replace "#content#" content)
      response/response))

(def component-template
  "<div data-component=\"%s\"><script type=\"application/edn\">%s</script></div>")

(def client-component (partial format component-template))

(defn client-options [options]
  (select-keys options [::dd/uri-prefix]))

(defn search [{{:keys [db options] :as context} ::dd/context :as request}]
  (layout
   (client-component
    "search"
    {:options (client-options options)
     :db
     {:schema {:db/ident {:db/unique :db.unique/identity}}
      :datoms (datomic/all-idents-as-datoms db (::dd/deprecated-attr options))}})))

(defn editor [{{:keys [options] :as context} ::dd/context :as request}]
  (layout
   (client-component
    "editor"
    (merge {:options (client-options options)}
           (select-keys context [:lookup-type :lookup-ref :entity :entity-stats])))))

(defn pprint-pre [val]
  (format "<pre style='white-space: pre-wrap;'>%s</pre>"
          (with-out-str (pprint/pprint val))))

(defn commit! [request]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "commit!:<br>"
                 (pprint-pre (::dd/context request))
                 (pprint-pre (transit/read-transit-body (:body request))))})
