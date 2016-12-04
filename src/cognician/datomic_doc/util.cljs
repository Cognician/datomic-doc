(ns cognician.datomic-doc.util
  (:require
    [cljs.spec :as s]
    [cognitect.transit :as t]))

(defn conform!
  "Like s/conform, but throws an error with s/explain-data on failure."
  ([spec x]
   (conform! spec x ""))
  ([spec x msg]
   (let [conformed (s/conform spec x)]
     (if (= ::s/invalid conformed)
       (throw (ex-info (str "Failed to conform " spec ", see ex-data")
                       {:data  (s/explain-data spec x)
                        :value x}))
       conformed))))

(defn read-transit-str [s]
  (t/read (t/reader :json) s))

(defn write-transit-str [o]
  (t/write (t/writer :json) o))
