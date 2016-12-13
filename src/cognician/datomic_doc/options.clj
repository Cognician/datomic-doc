(ns cognician.datomic-doc.options
  (:require [clojure.spec :as s]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic]
            [cognician.datomic-doc.spec :as spec]
            [datomic.api :as d]))

(s/def ::dd/datomic-uri      string?)
(s/def ::dd/datomic-uris     (s/coll-of ::dd/datomic-uri :kind set?))
(s/def ::dd/uri-prefix       string?)
(s/def ::dd/allow-write-pred fn?)
(s/def ::dd/allow-read-pred  fn?)
(s/def ::dd/deprecated-attr  keyword?)
(s/def ::dd/annotate-tx-fn   fn?)
(s/def ::dd/js-to-load       string?)

(s/def ::dd/config
  (s/keys :req [(or ::dd/datomic-uri ::dd/datomic-uris)]
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

(def wildcard-uri? (partial re-find #"^datomic:(.*)/\*$"))

(defn expand-wildcard-uri [uri]
  (into [] (map (partial str (string/replace uri #"\*$" "")))
        (d/get-database-names uri)))

(defn maybe-expand-wildcard-uri [{:keys [::dd/datomic-uri ::dd/datomic-uris] :as options}]
  (if (and datomic-uri (wildcard-uri? datomic-uri))
    (-> options
        (assoc ::dd/datomic-uris (expand-wildcard-uri datomic-uri))
        (dissoc ::dd/datomic-uri))
    options))

(defn db-uri->db-name [db-uri]
  (last (string/split db-uri #"/")))

(defn maybe-key-database-uris [{:keys [::dd/datomic-uris] :as options}]
  (cond-> options
    datomic-uris (assoc options ::dd/datomic-uris 
                        (into {} (map (juxt db-uri->db-name identity))
                              datomic-uris))))

(defn prepare-options [options]
  (-> default-options
      (merge (spec/conform! ::dd/config options))
      (update ::dd/uri-prefix (partial str "/"))
      maybe-expand-wildcard-uri
      maybe-key-database-uris))
