(ns cognician.datomic-doc.core
  (:require
    [clojure.string :as str]
    [cognician.datomic-doc.dom :as dom]
    [cognician.datomic-doc.util :as util]
    [rum.core :as rum]))

(enable-console-print!)

(defn update-server! [insight {:keys [body]}]
  (dom/ajax (str "/datomic-doc/" (:insight/uuid insight))
            (fn [result]
              (prn result))
            "POST"
            (some-> body util/write-transit-str)))


(rum/defc editor []
  [:div
   [:h2 "Datomic Doc"]])
