(ns cognician.datomic-doc.client.components.search
  (:require [cljs.core.async :refer [chan put!]]
            [clojure.string :as string]
            [datascript.core :as d]
            [rum.core :as rum]
            [cognician.datomic-doc.client.common :as common]))

(defonce type-ahead-chan (chan))

(defonce debounced-type-ahead-chan-loop
  (common/debounced-action-chan type-ahead-chan 500 ::search))

(defmethod common/perform-action ::search [current-state [_ query]]
  (assoc current-state :query
         (when (and query (not (string/blank? query)))
           query)))

(def regex-for-query #(js/RegExp. % "i"))

(defn make-filter-xform-from-query [query]
  (cond (re-find #"^/" query)
        (filter (comp (partial re-find (-> query
                                           (string/replace #"^/" "")
                                           regex-for-query))
                      name
                      :v))

        (re-find #"/$" query)
        (filter (comp (partial = (string/replace query #"/$" ""))
                      namespace
                      :v))

        :else
        (filter (comp (partial re-find (regex-for-query query))
                      str
                      :v))))

(defn search-idents [db query]
  (->> (d/datoms db :aevt :db/ident)
       (sequence (comp (make-filter-xform-from-query query)
                       (map :e)))
       (d/pull-many db '[*])))

(rum/defc search < rum/reactive [state]
  (let [{:keys [db options query]} (rum/react state)]
    [:div.container
     [:section.section

      [:input#search
       {:type        "text"
        :placeholder "Search on :db/ident"
        :auto-focus  "autofocus"
        :value       query
        :on-change   #(put! type-ahead-chan (.. % -currentTarget -value))}]
      
      [:hr]

      (when query
        (for [[namespace ident-entities] (->> (search-idents db query)
                                              (group-by (comp namespace :db/ident))
                                              (sort-by first))
              :let [namespace-label (when (nil? namespace) "(no namespace)")]]
          [:div {:key (or namespace namespace-label)}
           [:h3.subtitle (if namespace (str ":" namespace) namespace-label)]
           [:ul
            (for [ident-entity (sort-by (comp name :db/ident) ident-entities)
                  :let [name (-> ident-entity :db/ident name)]]
              [:li
               [:a {:href (str (:cognician.datomic-doc/uri-prefix options)
                               "/ident" 
                               (when-not (nil? namespace)
                                 (str "/" namespace)) 
                               "/" name)}
                ":" (when namespace (str namespace "/")) name]])]
           [:hr]]))]]))
              
