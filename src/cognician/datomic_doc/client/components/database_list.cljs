(ns cognician.datomic-doc.client.components.database-list
  (:require [cognician.datomic-doc.client.util :as util]
            [rum.core :as rum]))

(rum/defc database-list < rum/reactive [state]
  (let [{:keys [routes databases]} (rum/react state)]
    [:div.container
     [:section.section.db-list
      [:h1.title "Datomic Doc"]
      (for [[db-name db-uri] (sort-by first databases)]
        [:a.button.is-large
         {:key   db-name
          :href  (util/path-for routes :search {:db-name db-name})
          :title db-uri}
         db-name])]]))
