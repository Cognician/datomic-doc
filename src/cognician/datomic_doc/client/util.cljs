(ns cognician.datomic-doc.client.util
  (:require [cljs.core.async :refer [<! >! chan put! timeout]]
            [cljs.pprint :as pprint]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def key->label (comp string/capitalize name))

(defn go-to-url [url]
  (when url
    (set! (.. js/window -location -href) url)))

(defn debug-pre [m]
  [:pre {:style {:background    "#eee"
                 :border-radius "10px"
                 :font-family   "Fira Code"
                 :font-size     "0.8em"
                 :padding       "1em"
                 :white-space   "pre-wrap"}}
   (with-out-str (cljs.pprint/pprint m))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; core.async helpers

(defn page-load-timeout [ms]
  (let [timeout-channel (timeout ms)]
    (.addEventListener js/window "DOMContentLoaded"
                       #(put! timeout-channel [:page-loaded]))
    timeout-channel))

(defn debounce
  ([c ms] (debounce (chan) c ms false))
  ([c ms immediate] (debounce (chan) c ms immediate))
  ([c' c ms immediate]
   (go
     (loop [start (js/Date.) timeout nil]
       (let [loc (<! c)]
         (when timeout
           (js/clearTimeout timeout))
         (let [diff  (- (js/Date.) start)
               delay (if (and immediate
                              (or (>= diff ms)
                                  (not timeout)))
                       0 ms)
               t     (js/setTimeout #(go (>! c' loc)) delay)]
           (recur (js/Date.) t)))))
   c'))
