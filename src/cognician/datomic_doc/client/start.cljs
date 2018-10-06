(ns cognician.datomic-doc.client.start
  (:require [cljs.reader :as edn]
            [cognician.datomic-doc.routes :as routes]
            [datascript.core :as d]
            [goog.dom :as dom]
            [rum.core :as rum])
  (:import goog.dom.query))

(defn read-edn-from-inner-script-tag [element]
  (some->> (dom/getElementsByTagNameAndClass "script" nil element)
           array-seq
           first
           .-textContent
           edn/read-string))

(defn maybe-prepare-routes [state]
  (let [{:keys [uri-prefix multiple-databases?]} state]
    (cond-> state
      (and uri-prefix (some? multiple-databases?))
      (assoc :routes (routes/make-routes uri-prefix multiple-databases?)))))

(defn maybe-prepare-datascript-db [state]
  (if-let [{:keys [datoms schema]} (:db state)]
    (assoc state :db @(d/conn-from-datoms (map (partial apply d/datom) datoms) schema))
    state))

(defn prepare-component-state! [common-state element]
  (when-let [state (read-edn-from-inner-script-tag element)]
    (swap! common-state merge (-> state
                                  maybe-prepare-datascript-db
                                  maybe-prepare-routes))))

(def COMPONENT-ATTR "data-component")

(defn start-component! [common-state component-type->fn element]
  (when-let [component-fn (get component-type->fn (.getAttribute element COMPONENT-ATTR))]
    (rum/mount (component-fn common-state) element)))

(def COMPONENT-DOM-QUERY (str "[" COMPONENT-ATTR "]"))

(defn start-all-components! [common-state component-type->fn]
  (let [elements (array-seq (goog.dom.query COMPONENT-DOM-QUERY))]
    (doseq [element elements]
      (prepare-component-state! common-state element))
    (doseq [element elements]
      (start-component! common-state component-type->fn element))))
