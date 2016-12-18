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

(defn namespace-list-from-idents [db]
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
  (assoc-in current-state [:route-params :query]
            (when (and query (not (string/blank? query)))
              query)))

(rum/defc search-input [query]
  [:input#search
   {:type        "text"
    :placeholder "Search on :db/ident"
    :auto-focus  "autofocus"
    :value       query
    :on-change   #(put! type-ahead-chan (.. % -currentTarget -value))}])

(rum/defc namespace-list [routes route-params namespaces kind]
  (let [namespace-count (count namespaces)]
    [:div.namespace-list {:key kind}
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
             [:a {:href (util/path-for routes :search-with-query
                                       (assoc route-params :query (str item "/")))}
              item
              (when-not (contains? types :schema)
                [:span.tag.is-small (util/kw->label (first types))])]])]])]]))

(rum/defc result-list [routes route-params results]
  [:div
   [:.box (count results) " items found."]
   (for [[namespace ident-entities] (->> results
                                         (group-by (comp namespace :db/ident))
                                         (sort-by first))
         :let                       [namespace-label (when (nil? namespace) "(no namespace)")]]
     [:div {:key (or namespace namespace-label)}
      [:h3.subtitle (if namespace (str ":" namespace) namespace-label)]
      [:ul.attr-list
       (for [ident-entity (->> ident-entities
                               (sort-by (juxt :deprecated? (comp name :db/ident))))
             :let         [name (-> ident-entity :db/ident name)]]
         [:li {:key ident-entity}
          [:a {:href (util/path-for routes
                                    (if namespace :ident-detail-with-ns :ident-detail)
                                    (merge route-params
                                           (cond-> {:lookup-type "ident"
                                                    :name (string/replace name "?" "__Q")}
                                             namespace (assoc :ns namespace))))}
           ":" (when namespace (str namespace "/")) name]
          (when (:deprecated? ident-entity)
            [:span.tag.is-small.is-danger "Deprecated"])])]
      [:hr]])])

(rum/defc search < rum/reactive [state]
  (let [{:keys [routes route-params multiple-databases? db]} (rum/react state)
        query                                                (:query route-params)]
    [:div.container
     [:section.section
      [:h1.title "Datomic Doc"]
      (when multiple-databases?
        [:nav.nav
         [:.nav-left.nav-menu
          [:span.nav-item
           [:a.button {:href (util/path-for routes :database-list)} "Databases"]]]])
      (search-input query)
      (if query
        (result-list routes route-params (search-idents db query))
        (let [namespaces (namespace-list-from-idents db)]
          (list
           (rum/with-key (namespace-list routes route-params
                                         (remove second namespaces) :active)
             :active)
           (rum/with-key (namespace-list routes route-params
                                         (filter second namespaces) :deprecated)
             :deprecated))))]]))
