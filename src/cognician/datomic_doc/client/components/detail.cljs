(ns cognician.datomic-doc.client.components.detail
  (:require [clojure.string :as string]
            [cognician.datomic-doc.client.util :as util]
            [rum.core :as rum]))

(rum/defc metadata [lookup-type
                    {:keys [db/valueType db/cardinality db/unique db/index
                            db/isComponent db/noHistory db/fulltext deprecated?]}
                    {:keys [created last-touched datom-count]}]
  [:.box.metadata
   [:strong "Created: "] (util/format-date created) ". "
   (when last-touched
     (list
      [:strong {:key "last-touched"} "Last touched: "]
      (util/format-date last-touched) ". "))
   (when datom-count
     (list
      [:strong {:key "appearances"} "Appearances: "]
      (util/format-number datom-count) "."))
   (when (and (= :enum lookup-type) deprecated?)
     [:div.tags-list
      [:span.tag.is-danger "Deprecated"]])
   (when (= :schema lookup-type)
     [:div.tags-list
      (when deprecated?
        [:span.tag.is-danger "Deprecated"])
      [:span.tag.is-primary (util/kw->label valueType)]
      (when (= cardinality :db.cardinality/many)
        [:span.tag.is-warning "Many"])
      (when unique
        [:span.tag.is-info "Unique: " (util/kw->label unique)])
      (when (and index (not unique) (not= valueType :db.type/ref))
        [:span.tag "Indexed"])
      (when isComponent
        [:span.tag "Component"])
      (when noHistory
        [:span.tag "No History"])
      (when fulltext
        [:span.tag "Full-text Indexed"])])])

(def entity?+ns?->edit-route
  {[false true]  :ident-edit-with-ns
   [false false] :ident-edit
   [true true]   :entity-edit-with-ns
   [true false]  :entity-edit})

(rum/defc detail < rum/reactive [state]
  (let [{:keys [routes route-params read-only? lookup-type lookup-ref
                entity entity-stats]} (rum/react state)]
    [:div.container
     [:section.section
      [:h1.title "Datomic Doc"]
      [:nav.nav
       [:.nav-left.nav-menu
        [:span.nav-item
         [:a.button {:href (util/path-for routes :search route-params)} "Search"]
         (when (contains? #{:schema :enum} lookup-type)
           (let [query (str (namespace lookup-ref) "/")]
             [:a.button {:href (util/path-for routes :search-with-query
                                              (assoc route-params :query query))}
              "Search \"" query "\""]))]]]
      [:h1.title
       [:strong (util/kw->label lookup-type)] " "
       (if (= :entity lookup-type)
         (string/join " " lookup-ref)
         (str lookup-ref))]
      [:hr]
      (metadata lookup-type entity entity-stats)
      [:hr]
      [:iframe.markdown-editor
       {:src (util/path-for routes
                            (get entity?+ns?->edit-route
                                 [(= :entity lookup-type) (boolean (:ns route-params))])
                            route-params)}]]]))
