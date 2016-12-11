(ns cognician.datomic-doc.client.util
  (:require [cljs.core.async :refer [<! >! chan put! timeout]]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            goog.i18n.DateTimeFormat
            goog.i18n.NumberFormat)
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Formatting

(def kw->label (comp string/capitalize name))

(def date-format 
  (goog.i18n.DateTimeFormat. (.-MEDIUM_DATE goog.i18n.DateTimeFormat.Format)))

(defn format-date [date]
  (.format date-format (js/Date. date)))

(def number-format
  (goog.i18n.NumberFormat. goog.i18n.NumberFormat.Format.DECIMAL))

(defn format-number [number]
  (.format number-format number))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; core.async

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; pretty print data

(defn debug-pre [m]
  [:pre {:style {:background    "#eee"
                 :border-radius "10px"
                 :font-family   "Fira Code"
                 :font-size     "0.8em"
                 :padding       "1em"
                 :white-space   "pre-wrap"}}
   (with-out-str (cljs.pprint/pprint m))])
