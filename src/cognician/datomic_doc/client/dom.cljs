(ns cognician.datomic-doc.dom
  (:require
    [goog.userAgent :as ua]
    [goog.net.XhrIo]
    [cognician.datomic-doc.util :as util]))

(defn ajax
  ([url callback] (ajax url callback "GET" ""))
  ([url callback method] (ajax url callback method ""))
  ([url callback method body]
   (let [url (if ua/IE
               (str url (if (re-find #"\?" url) "&" "?") "rand=" (rand))
               url)]
     (.send goog.net.XhrIo url
       (fn [reply]
         (let [xhr    (.-target reply)
               status (.getStatus xhr)
               text   (.getResponseText xhr)]
           (when (== 200 status)
             (when callback
               (callback (util/read-transit-str text))))))
       method
       body))))
