(ns cognician.datomic-doc.client.start
  (:require [cljs.reader :as edn]
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

(defn maybe-prepare-datascript-db [state]
  (if-let [{:keys [datoms schema]} (:db state)]
    (assoc state :db @(d/conn-from-datoms (map (partial apply d/datom) datoms) schema))
    state))

(defn prepare-component-state! [common-state element]
  (when-let [state (read-edn-from-inner-script-tag element)]
    (swap! common-state merge (maybe-prepare-datascript-db state))))

(def COMPONENT-ATTR "data-component")

(defn start-component! [common-state component-type->fn element]
  (when-let [component-fn (get component-type->fn
                               (.getAttribute element COMPONENT-ATTR))]
    (rum/mount (component-fn common-state) element)))

(def COMPONENT-DOM-QUERY (str "[" COMPONENT-ATTR "]"))

(defn start-all-components! [common-state component-type->fn]
  (let [components (array-seq (goog.dom.query COMPONENT-DOM-QUERY))]
    (doseq [element components]
      (prepare-component-state! common-state element))
    (doseq [element components]
      (start-component! common-state component-type->fn element))))
