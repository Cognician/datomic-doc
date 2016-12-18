(ns cognician.datomic-doc.client.common
  (:require [cljs.core.async :refer [<! chan put!]]
            [cognician.datomic-doc.client.util :as util])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def initial-state {})

(defonce state (atom initial-state))

(defonce action-chan (chan))

(defn debounced-action-chan [chan ms action]
  (let [debounced-chan (util/debounce chan ms)]
    (go (loop []
          (when-let [val (<! debounced-chan)]
            (put! action-chan [action val])
            (recur))))))

(defmulti perform-action (fn [current-state action] (first action)))

(defn handle-action! [state action]
  (swap! state
         (fn [current-state]
           (perform-action current-state action))))

(defonce event-loop
  (go (<! (util/page-load-timeout 300))
      (loop [action (<! action-chan)]
        (when action
          (handle-action! state action)
          (recur (<! action-chan))))))
