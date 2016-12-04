(ns cognician.datomic-doc.gallery
  (:require
    [cognician.datomic-doc.core :as datomic-doc]
    [rum.core :as rum]))

(rum/defc gallery []
  [:div
   [:h1 "Gallery"]
   [:hr]
   (datomic-doc/editor)])

(defn ^:export refresh []
  (rum/mount (gallery) (js/document.getElementById "mount")))
