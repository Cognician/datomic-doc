(ns cognician.datomic-doc.transit
  (:require
    [cognitect.transit :as t])
  #?(:clj 
     (:import
      [java.io ByteArrayInputStream ByteArrayOutputStream InputStream])))

#?(:clj
   (defn read-transit [is]
    (t/read (t/reader is :json))))

#?(:clj
   (defn read-transit-str [^String s]
     (read-transit (ByteArrayInputStream. (.getBytes s "UTF-8"))))
   
   :cljs
   (defn read-transit-str [s]
     (t/read (t/reader :json) s)))

#?(:clj
   (defn read-transit-body [^java.io.InputStream body]
     (when (some? body)
       (.reset body) ;; resets org.httpkit.BytesInputStream to the beginning
       (read-transit body))))

#?(:clj
   (defn write-transit [o os]
     (t/write (t/writer os :json) o)))

#?(:clj
   (defn write-transit-bytes ^bytes [o]
     (let [os (ByteArrayOutputStream.)]
       (write-transit o os)
       (.toByteArray os))))

#?(:clj
   (defn write-transit-str [o]
     (String. (write-transit-bytes o) "UTF-8"))
   
   :cljs
   (defn write-transit-str [o]
     (t/write (t/writer :json) o)))

#?(:clj
   (defn transit-response [response]
     (some-> response
       (update :headers assoc 
               "Content-Type" "application/transit+json; charset=utf-8")
       (update :body write-transit-str))))

#?(:clj
   (defn wrap-transit [handler]
     (fn [request]
       (some-> (handler request) transit-response))))
