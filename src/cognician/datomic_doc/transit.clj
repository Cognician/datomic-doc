(ns cognician.datomic-doc.transit
  (:require
    [cognitect.transit :as t])
  (:import
    [java.io ByteArrayOutputStream ByteArrayInputStream]))

(defn write-transit [o os]
  (t/write (t/writer os :json) o))

(defn write-transit-bytes ^bytes [o]
  (let [os (ByteArrayOutputStream.)]
    (write-transit o os)
    (.toByteArray os)))
    
(defn write-transit-str [o]
  (String. (write-transit-bytes o) "UTF-8"))

(defn wrap-transit [handler]
  (fn [request]
    (some-> (handler request)
      (update :headers assoc "Content-Type" "application/transit+json; charset=utf-8")
      (update :body    write-transit-str))))

(defn read-transit [is]
  (t/read (t/reader is :json)))

(defn read-transit-str [^String s]
  (read-transit (ByteArrayInputStream. (.getBytes s "UTF-8"))))
