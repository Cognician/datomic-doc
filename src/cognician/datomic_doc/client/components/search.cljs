(ns cognician.datomic-doc.client.components.search
  (:require [cljs.core.async :refer [chan put!]]
            [clojure.string :as string]
            [datascript.core :as d]
            [rum.core :as rum]
            [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.util :as util]
            goog.Uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Data preparation

(defn namespace-list [db]
  (->> (d/datoms db :aevt :db/ident)
       (map (comp #(d/pull db '[*] %) :e))
       (filter (comp namespace :db/ident))
       (group-by (comp namespace :db/ident))
       (map (fn [[ns entities]]
              [ns 
               (every? :deprecated? entities) 
               (set (map :ident-type entities))]))
       distinct
       (sort-by first)))

(def regex-for-query #(js/RegExp. % "i"))

(defn make-filter-xform-from-query [query]
  (cond 
    (re-find #"^/" query)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; UI Components

(defonce type-ahead-chan (chan))

(defonce debounced-type-ahead-chan-loop
  (common/debounced-action-chan type-ahead-chan 500 ::search))

(defmethod common/perform-action ::search [current-state [_ query]]
  (assoc current-state :query
         (when (and query (not (string/blank? query)))
           query)))

(rum/defc search-input [query]
  [:input#search
   {:type        "text"
    :placeholder "Search on :db/ident"
    :auto-focus  "autofocus"
    :value       query
    :on-change   #(put! type-ahead-chan (.. % -currentTarget -value))}])

(rum/defc namespace-list [options namespaces kind]
  (let [namespace-count (count namespaces)]
    [:div {:key kind}
     [:h2.title
      (when (= :deprecated kind)
        "Deprecated ")
      "Namespaces (" namespace-count ")"]
     [:.columns
      (for [col (partition-all (js/Math.ceil (/ namespace-count 4)) namespaces)]
        [:.column {:key col}
         [:ul.attr-list
          (for [[item _ types] col]
            [:li {:key item}
             [:a {:href (str (:cognician.datomic-doc/uri-prefix options)
                            "?query=" item "/")}
              item
              (when-not (contains? types :schema)
                [:span.tag.is-small (util/kw->label (first types))])]])]])]]))

(rum/defc result-list [options results]
  [:div
   [:.box (count results) " items found."] 
   (for [[namespace ident-entities] (->> results
                                         (group-by (comp namespace :db/ident))
                                         (sort-by first))
         :let [namespace-label (when (nil? namespace) "(no namespace)")]]
     [:div {:key (or namespace namespace-label)}
      [:h3.subtitle (if namespace (str ":" namespace) namespace-label)]
      [:ul.attr-list
       (for [ident-entity (->> ident-entities
                               (sort-by (juxt :deprecated? (comp name :db/ident))))
             :let [name (-> ident-entity :db/ident name)]]
         [:li {:key ident-entity}
          [:a {:href (str (:cognician.datomic-doc/uri-prefix options)
                          "/ident"
                          (when-not (nil? namespace)
                            (str "/" namespace))
                          "/" (string/replace name "?" "__Q"))}
           ":" (when namespace (str namespace "/")) name]
          (when (:deprecated? ident-entity)
            [:span.tag.is-small.is-danger "Deprecated"])])]
      [:hr]])])

(rum/defc search <
  rum/reactive
  {:will-mount (fn [state]
                 (when-let [query (.. (goog.Uri. js/window.location)
                                      getQueryData
                                      (get "query"))]
                   (swap! (first (:rum/args state)) assoc :query query))
                 state)}
  [state]
  (let [{:keys [options db query]} (rum/react state)]
    [:div.container
     [:section.section
      (search-input query)
      (if query
        (result-list options (search-idents db query))
        (let [namespaces (namespace-list db)]
          (list
           (rum/with-key (namespace-list options (remove second namespaces) :active)
                         :active)
           [:br] [:br]
           (rum/with-key (namespace-list options (filter second namespaces) :deprecated)
                         :deprecated))))]]))
        
