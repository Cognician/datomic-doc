(ns cognician.datomic-doc.client.components.editor-buttons
  (:require [clojure.string :as string]
            [cognician.datomic-doc.client.util :as util]
            [rum.core :as rum]))

(def dirty-message "You have unsaved changes. Discard them?")

(defn handle-dirty-state [e]
  (let [e (or e (.-event js/window))]
    (set! (.-returnValue e) dirty-message))
  dirty-message)

(defn save-content! [dirty]
;; - Ajax call to save content.
;; - Error message modal: 
;;   - When a db.fn/cas fail, show link to edit in new tab (showing any unanticipated changes) and maintain dirty state.
  (prn :save)
  (reset! dirty false))

(rum/defcs editor-buttons < 
  (rum/local false ::dirty)
  {:did-mount (fn [state]
                (js/setTimeout
                 (fn []
                   (.on js/editor "change" #(reset! (::dirty state) true))
                   (set! (.-onbeforeunload js/window) #(when @(::dirty state)
                                                         (handle-dirty-state %))))
                 0) 
                state)}
  [{:keys [::dirty]} state]
  (let [{:keys [routes route route-params]} @state
        route (-> route name (string/replace "edit" "detail") keyword)]
    [:span
     [:button.ui-button.ui-widget.ui-corner-all 
      (if @dirty
        {:on-click #(save-content! dirty)}
        {:disabled "disabled"
         :class    ["ui-button-disabled" "ui-state-disabled"]})
      "Save"]
     [:a.ui-button.ui-widget.ui-corner-all 
      {:href (util/path-for routes route route-params)} "Cancel"]]))
