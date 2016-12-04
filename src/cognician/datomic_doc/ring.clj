(ns cognician.datomic-doc.ring
  (:require
    [clojure.string :as str]
    [datomic.api :as d]
    [cognician.datomic-doc.transit :as transit])
  (:import
    [java.io InputStream]))

(declare search editor save)

(defn read-body [^InputStream body]
  (when (some? body)
    (.reset body) ;; resets org.httpkit.BytesInputStream to the beginning
    (transit/read-transit body)))

(def access-denied
  { :status  403
    :headers { "Content-Type" "text/plain; charset=utf-8"}
    :body    "Access denied"})

(defn handler [{:keys [request-method uri conn body]}]
  (when (str/starts-with? uri "/datomic-doc/")
    (let [db        (d/db conn)
          ;; /datomic-doc/:uuid
          [_ uuid] (str/split uri #"/")]
      (case request-method
        :get
        (cond
          (nil? uuid) ;; GET /datomic-doc
          (search db)
          uuid ;; GET /datomic-doc/:uuid
          (editor db uuid))
        :post
        (cond
          (nil? uuid) ;; POST /datomic-doc
          access-denied
          uuid ;; POST /datomic-doc/:uuid
          (save db uuid (read-body body)))))))

(def api (transit/wrap-transit handler))
