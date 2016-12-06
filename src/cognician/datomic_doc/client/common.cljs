(ns cognician.datomic-doc.client.common
  (:require [cljs.core.async :refer [<! chan put!]]
            [cognician.datomic-doc.client.util :as util])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Shared app state - shared by *all* components

(def initial-state {::effects []})

(defonce state (atom initial-state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Action channel

(defonce action-chan (chan))

(defn debounced-action-chan [chan ms action]
  (let [debounced-chan (util/debounce chan ms)]
    (go (loop []
          (when-let [val (<! debounced-chan)]
            (put! action-chan [action val])
            (recur))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actions

(defmulti perform-action (fn [current-state action] (first action)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Effects

(defmulti effect (fn [state channel action] (first action)))

(defn add-effect [state effect]
  (update state ::effects
          (fn [effects]
            (vec (set (conj effects effect))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Common state event loop

(defn handle-action! [state action]
  (swap! state
         (fn [current-state]
           (let [new-state (-> current-state
                               (assoc ::effects []) ;; reset effects queue
                               (perform-action action))]
             ;;(prn :handle-action! action)
             (doseq [effect (::effects new-state)]
               (effect new-state action-chan effect))
             new-state))))

(defonce event-loop
  (go (<! (util/page-load-timeout 300))
      (loop [action (<! action-chan)]
        (when action
          (handle-action! state action)
          #_(try
              (handle-action! state action)
              (catch js/Object e
                (prn "Action error: " action)
                state))
          (recur (<! action-chan))))))
