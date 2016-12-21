(ns cognician.datomic-doc.client.components.editor-buttons
  (:require [cljs.reader :as edn]
            [clojure.string :as string]
            [cognician.datomic-doc.client.util :as util]
            [rum.core :as rum]))

(def dirty-message "You have unsaved changes. Discard them?")

(defn alter-route [routes route route-params new-action]
  (util/path-for routes (-> route name (string/replace "edit" new-action) keyword)
                 route-params))

(defn handle-dirty-state [e]
  (let [e (or e (.-event js/window))]
    (set! (.-returnValue e) dirty-message))
  dirty-message)

(def default-error-message "Sorry, there was an error saving your changes.")

(def error-messages
  {:error default-error-message
   :db.error/cas-failed
   "Sorry, there was an error saving your changes, because someone else has already altered this content.

Please open this document in a new tab to see what's new."})

(defn save-content-result [dirty body]
  (let [result (edn/read-string body)]
    (if (= :ok result)
      (reset! dirty false)
      (js/window.alert (get error-messages result default-error-message)))))

(defn save-content! [save-route dirty]
  (util/ajax save-route (.getValue js/editor) #(save-content-result dirty %)))

(rum/defcs editor-buttons < 
  (rum/local false ::dirty)
  {:did-mount (fn [state]
                (js/setTimeout
                 (fn []
                   (.on js/editor "change" #(reset! (::dirty state) true))
                   (set! (.-onbeforeunload js/window)
                         #(when @(::dirty state)
                            (handle-dirty-state %))))
                 0) 
                state)}
  [{:keys [::dirty]} state]
  (let [{:keys [routes route route-params]} @state]
    [:span
     [:button.ui-button.ui-widget.ui-corner-all 
      (if @dirty
        {:on-click #(save-content! (alter-route routes route route-params "save") dirty)}
        {:disabled "disabled"
         :class    ["ui-button-disabled" "ui-state-disabled"]})
      "Save"]
     [:a.ui-button.ui-widget.ui-corner-all 
      {:href (alter-route routes route route-params "detail")}
      (if @dirty "Cancel" "Close")]]))
