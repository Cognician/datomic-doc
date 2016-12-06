(ns cognician.datomic-doc.client.components.editor
  (:require [cljs.core.async :refer [chan put!]]
            [clojure.string :as string]
            [datascript.core :as d]
            [rum.core :as rum]
            [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.util :as util]))

(rum/defc editor < rum/reactive [state]
  (let [{:keys [options lookup-type lookup-ref entity entity-stats]} (rum/react state)]
    [:div.container
     [:section.section
      [:nav.nav
       [:.nav-left.nav-menu
        [:a.nav-item.button {:href (:cognician.datomic-doc/uri-prefix options)}
         "Search"]]]
      [:h1.title 
       [:strong (util/kw->label lookup-type)]
       " "
       (case lookup-type
         :ident (str lookup-ref)
         :entity (string/join " " lookup-ref))]
      [:hr]
      (util/debug-pre entity)
      [:hr]
      (util/debug-pre entity-stats)]]))
