(ns cognician.datomic-doc.client.components.search
  (:require [cljs.core.async :refer [chan put!]]
            [clojure.string :as string]
            [datascript.core :as d]
            [rum.core :as rum]
            [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.util :as util]
            goog.Uri))

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

(rum/defc search <
  rum/reactive
  {:did-mount (fn [state]
                (when-let [query (.. (goog.Uri. js/window.location)
                                     getQueryData
                                     (get "query"))]
                  (put! type-ahead-chan query))
                state)}
  [state]
  (let [{:keys [db options query]} (rum/react state)]
    [:div.container
     [:section.section
      [:input#search
       {:type        "text"
        :placeholder "Search on :db/ident"
        :auto-focus  "autofocus"
        :value       query
        :on-change   #(put! type-ahead-chan (.. % -currentTarget -value))}]
      (when-not query
        (let [namespaces (->> (d/datoms db :aevt :db/ident)
                              (sequence (keep (comp namespace :v)))
                              distinct
                              sort)]
          (list
           [:h2.title "Namespaces"]
           [:.columns
            (for [col (partition-all (js/Math.ceil (/ (count namespaces) 4)) namespaces)]
              [:.column
               [:ul
                (for [item col]
                  [:li
                   [:a {:href "javascript:"
                        :on-click #(put! type-ahead-chan item)}
                    item]])]])])))
      (when query
        (let [results (search-idents db query)
              result-count (count results)
              results (->> results
                           (group-by (comp namespace :db/ident))
                           (sort-by first)
                           doall)]
          (list
           [:.box result-count " items found."]
           
           (for [[namespace ident-entities] results
                 :let                       [namespace-label (when (nil? namespace) "(no namespace)")]]
             [:div {:key (or namespace namespace-label)}
              [:h3.subtitle (if namespace (str ":" namespace) namespace-label)]
              [:ul
               (for [ident-entity (sort-by (comp name :db/ident) ident-entities)
                     :let         [name (-> ident-entity :db/ident name)]]
                 [:li
                  [:a {:href (str (:cognician.datomic-doc/uri-prefix options)
                                  "/ident"
                                  (when-not (nil? namespace)
                                    (str "/" namespace))
                                  "/" name)}
                   ":" (when namespace (str namespace "/")) name]])]
              [:hr]]))))]]))
