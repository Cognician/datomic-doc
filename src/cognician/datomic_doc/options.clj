(ns cognician.datomic-doc.options
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.spec :as spec]
            [datomic.api :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::dd/datomic-uri      string?)
(s/def ::dd/datomic-uris     (s/coll-of ::dd/datomic-uri :kind set?))
(s/def ::dd/uri-prefix       string?)
(s/def ::dd/allow-write-pred fn?)
(s/def ::dd/allow-read-pred  fn?)
(s/def ::dd/deprecated-attr  keyword?)
(s/def ::dd/count-datoms?    boolean?)
(s/def ::dd/annotate-tx-fn   fn?)
(s/def ::dd/dev-mode?        boolean?)

(s/def ::dd/config
  (s/keys :req [(or ::dd/datomic-uri ::dd/datomic-uris)]
          :opt [::dd/uri-prefix
                ::dd/allow-write-pred
                ::dd/allow-read-pred
                ::dd/deprecated-attr
                ::dd/count-datoms?
                ::dd/annotate-tx-fn
                ::dd/dev-mode?]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Parse options

(def default-options
  {::dd/uri-prefix       "dd"
   ::dd/allow-write-pred (constantly false)
   ::dd/allow-read-pred  (constantly false)
   ::dd/count-datoms?    true})

(def wildcard-uri? (partial re-find #"^datomic:(.*)/\*$"))

(defn maybe-set-multiple-databases? [{:keys [::dd/datomic-uri ::dd/datomic-uris]
                                      :as   options}]
  (let [wildcard-uri? (and datomic-uri (wildcard-uri? datomic-uri))]
    (if (or datomic-uris wildcard-uri?)
      (cond-> options
        true          (assoc ::dd/multiple-databases? true)
        wildcard-uri? (assoc ::dd/wildcard-uri? true))
      options)))

(defn prepare-options [options]
  (-> default-options
      (merge (spec/conform! ::dd/config options))
      (update ::dd/uri-prefix (partial str "/"))
      maybe-set-multiple-databases?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Multiple databases

(defn expand-wildcard-uri [{:keys [::dd/datomic-uri] :as options}]
  (-> options
      (assoc ::dd/datomic-uris
             (into [] (map (partial str (string/replace datomic-uri #"\*$" "")))
                   (d/get-database-names datomic-uri)))
      (dissoc ::dd/datomic-uri)))

(defn db-uri->db-name [db-uri]
  (last (string/split db-uri #"/")))

(defn key-database-uris [{:keys [::dd/datomic-uris] :as options}]
  (assoc options ::dd/datomic-uris
         (into (sorted-map) (map (juxt db-uri->db-name identity))
               datomic-uris)))

(defn maybe-prepare-database-uris [{:keys [::dd/multiple-databases? ::dd/wildcard-uri?]
                                    :as   options}]
  (if multiple-databases?
    (cond-> options
      wildcard-uri? expand-wildcard-uri
      true          key-database-uris)
    options))
