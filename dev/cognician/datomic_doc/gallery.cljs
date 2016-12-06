(ns cognician.datomic-doc.gallery
  (:require
    [cognician.datomic-doc.client :as client]))

(defn ^:export refresh []
  (client/start-client!))
