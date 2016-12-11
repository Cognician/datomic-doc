(ns cognician.datomic-doc.options
  (:require [clojure.spec :as s]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.spec :as spec]))

(s/def ::dd/datomic-uri      string?)
(s/def ::dd/uri-prefix       string?)
(s/def ::dd/allow-write-pred fn?)
(s/def ::dd/allow-read-pred  fn?)
(s/def ::dd/deprecated-attr  keyword?)
(s/def ::dd/annotate-tx-fn   fn?)
(s/def ::dd/js-to-load       string?)

(s/def ::dd/config
  (s/keys :req [::dd/datomic-uri]
          :opt [::dd/uri-prefix
                ::dd/allow-write-pred
                ::dd/allow-read-pred
                ::dd/deprecated-attr
                ::dd/annotate-tx-fn
                ::dd/js-to-load]))

(def default-options
  {::dd/uri-prefix       "dd"
   ::dd/allow-write-pred (constantly false)
   ::dd/allow-read-pred  (constantly false)
   ::dd/annotate-tx-fn   identity
   ::dd/js-to-load       "main.min.js"})

(defn prepare-options [options]
  (-> default-options
      (merge (spec/conform! ::dd/config options))
      (update ::dd/uri-prefix (partial str "/"))))
