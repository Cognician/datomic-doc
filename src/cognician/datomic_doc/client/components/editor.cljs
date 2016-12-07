(ns cognician.datomic-doc.client.components.editor
  (:require [cljs.core.async :refer [chan put!]]
            [clojure.string :as string]
            [datascript.core :as d]
            [rum.core :as rum]
            [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.util :as util]))

(rum/defc metadata [{:keys [cognician.datomic-doc/deprecated-attr]}
                    lookup-type
                    {:keys [db/valueType db/cardinality db/unique db/index
                            db/isComponent db/noHistory db/fulltext]
                     :as entity}
                    {:keys [created last-touched datom-count]}]
  [:.box.metadata
   [:strong "Created: "] (util/format-date :medium-date created) ". "
   (when last-touched
     (list [:strong "Last touched: "] (util/format-date :medium-date last-touched) ". "))
   (when-not (zero? datom-count)
     (list [:strong "Appearances: "] (util/format-number datom-count) "."))
   (when (and (= :enum lookup-type) (get entity deprecated-attr))
     (list
      [:br] [:br]
      [:span.tag.is-danger "Deprecated"]))
   (when (= :schema lookup-type)
     (list
      [:br] [:br]
      (when (get entity deprecated-attr)
        [:span.tag.is-danger "Deprecated"])
      [:span.tag.is-primary (util/kw->label valueType)]
      (when (= cardinality :db.cardinality/many)
        [:span.tag.is-warning "Many"])
      (when unique
        [:span.tag.is-info "Unique: " (util/kw->label unique)])
      (when (and (not unique) index)
        [:span.tag "Indexed"])
      (when isComponent
        [:span.tag "Component"])
      (when noHistory
        [:span.tag "No History"])
      (when fulltext
        [:span.tag "Full-text Indexed"])))])

(rum/defc editor < rum/reactive [state]
  (let [{:keys [options lookup-type lookup-ref entity entity-stats]} (rum/react state)
        {:keys [:cognician.datomic-doc/uri-prefix]} options]
    [:div.container
     [:section.section
      [:nav.nav
       [:.nav-left.nav-menu
        [:span.nav-item
         [:a.button {:href uri-prefix}
          "Search"]
         (when (contains? #{:schema :enum} lookup-type)
           (let [query (str (namespace lookup-ref) "/")]
             [:a.button {:href (str uri-prefix "?query=" query)}
              "Search \"" query "\""]))]]]
      [:h1.title 
       [:strong (util/kw->label lookup-type)]
       " "
       (if (= :entity lookup-type)
         (string/join " " lookup-ref)
         (str lookup-ref))]
      [:hr]
      (metadata options lookup-type entity entity-stats)
      [:hr]
      (if-let [doc (:db/doc entity)]
        [:div doc]
        [:p "No documentation yet."])]]))
