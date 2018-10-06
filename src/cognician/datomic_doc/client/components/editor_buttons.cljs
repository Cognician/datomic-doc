(ns cognician.datomic-doc.client.components.editor-buttons
  (:require [cljs.reader :as edn]
            [clojure.string :as string]
            [cognician.datomic-doc.client.util :as util]
            [rum.core :as rum]))

(defn load-content-result [unsaved body]
  (.setValue js/editor body -1)
  (reset! unsaved false))

(defn load-content! [load-route unsaved]
  (util/ajax-get load-route #(load-content-result unsaved %)))

(def unsaved-message "You have unsaved changes. Discard them?")

(defn alter-route [routes route route-params new-action]
  (util/path-for routes (-> route name (string/replace "edit" new-action) keyword)
                 route-params))

(defn handle-unsaved-state [e]
  (let [e (or e (.-event js/window))]
    (set! (.-returnValue e) unsaved-message))
  unsaved-message)

(def default-error-message "Sorry, there was an error saving your changes.")

(def error-messages
  {:error default-error-message
   :db.error/cas-failed
   "Sorry, there was an error saving your changes, because someone else has already altered this content.

Please open this document in a new tab to see what's new."})

(defn save-content-result [unsaved body]
  (let [result (edn/read-string body)]
    (if (= :ok result)
      (reset! unsaved false)
      (js/window.alert (get error-messages result default-error-message)))))

(defn save-content! [save-route unsaved]
  (util/ajax-post save-route (.getValue js/editor) #(save-content-result unsaved %)))

(rum/defcs editor-buttons <
  rum/reactive
  (rum/local false ::unsaved)
  {:did-mount (fn [{:keys [::unsaved rum/args] :as state}]
                (js/setTimeout
                 (fn []
                   ;; load the existing content
                   (let [{:keys [routes route route-params]} @(first args)]
                     (load-content! (alter-route routes route route-params "doc")
                                    unsaved))
                   ;; track unsaved state
                   (.on js/editor "change" #(reset! unsaved true))
                   ;; prevent closing the tab when unsaved
                   (set! (.-onbeforeunload js/window)
                         #(when @unsaved
                            (handle-unsaved-state %))))
                 0)
                state)}
  [{*unsaved ::unsaved} state]
  (let [{:keys [routes route route-params read-only?]} (rum/react state)]
    (when-not read-only?
      [:span
       [:button.ui-button.ui-widget.ui-corner-all
        (if @*unsaved
          {:on-click #(save-content! (alter-route routes route route-params "save")
                                     *unsaved)}
          {:disabled "disabled"
           :class    ["ui-button-disabled" "ui-state-disabled"]})
        "Save"]
       [:a.ui-button.ui-widget.ui-corner-all
        {:href (alter-route routes route route-params "detail")}
        (if @*unsaved "Cancel" "Close")]])))
