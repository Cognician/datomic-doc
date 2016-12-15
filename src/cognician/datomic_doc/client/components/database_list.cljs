(ns cognician.datomic-doc.client.components.database-list
  (:require [rum.core :as rum]))

(rum/defc database-list < rum/reactive [state]
  (let [{:keys [uri-prefix databases]} (rum/react state)]
    [:div.container
     [:section.section.db-list 
      [:h1.title "Datomic Doc"]
      (for [[db-name db-uri] (sort-by first databases)]
        [:a.button.is-large 
         {:href (str uri-prefix "/" db-name)
          :title db-uri}
         db-name])]]))
