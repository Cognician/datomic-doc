(ns cognician.datomic-doc.datomic
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [cognician.datomic-doc :as dd]
            [datomic.api :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fetch Entity

(def pull-spec
  [:db/id
   :db/doc
   :db/ident
   {:db/valueType [:db/ident]}
   {:db/cardinality [:db/ident]}
   {:db/unique [:db/ident]}
   :db/index
   :db/noHistory
   :db/isComponent
   :db/fulltext])

(defn flatten-idents [m]
  (walk/postwalk (fn [item]
                   (if (and (map? item) (:db/ident item) (= 1 (count (keys item))))
                     (:db/ident item)
                     item))
                 m))

(defn pull-entity [db lookup-ref deprecated-attr]
  (let [entity (-> (d/pull db (cond-> pull-spec
                                deprecated-attr (conj deprecated-attr))
                           lookup-ref)
                   flatten-idents)]
    (cond-> entity
      (get entity deprecated-attr) (assoc :deprecated? true))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Entity Stats

(defn tx->tx-instant [db tx]
  (->> tx (d/entity db) :db/txInstant))

(defn entity-stats [db lookup-type lookup-ref count-datoms?]
  (let [history-db (d/history db)
        index      (case lookup-type
                     :partition          nil
                     :schema             :aevt
                     :enum               :vaet
                     (:function :entity) :eavt)]
    (cond-> {:created
             (->> (d/datoms history-db :eavt lookup-ref)
                  (transduce (map :tx) min Long/MAX_VALUE)
                  (tx->tx-instant db))}
      (contains? #{:schema :enum :function :entity} lookup-type)
      (assoc :last-touched
             (->> (d/datoms history-db index lookup-ref)
                  (transduce (map :tx) max 0)
                  (tx->tx-instant db)))
      (and count-datoms? (contains? #{:schema :enum :entity} lookup-type))
      (assoc :datom-count
             (-> (d/datoms db index lookup-ref)
                 seq
                 count)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Assert :db/doc

(defn save-db-doc! [db-uri {:keys [lookup-ref entity]} body tx-map]
  (try
    @(d/transact (d/connect db-uri)
                 [tx-map
                  [:db.fn/cas lookup-ref :db/doc (:db/doc entity) (slurp body)]])
    :ok
    (catch Throwable e
      (or (-> e .getCause ex-data :db/error) :error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Datoms for Datascript

(defn datomic-schema? [attr]
  (let [ns (str (namespace attr))]
    (or (string/starts-with? ns "db")
        (string/starts-with? ns "fressian"))))

(defn classify-ident [db lookup-ref]
  (let [entity (d/entity db lookup-ref)]
    (cond
      (:db/valueType entity)          :schema
      (:db.install/_partition entity) :partition
      (:db/fn entity)                 :function
      :else                           :enum)))

(defn expand-ident-datom [db deprecated-attr [e _ v]]
  (let [entity (d/entity db e)]
    (cond-> [[e :db/ident v]
             [e :ident-type (classify-ident db v)]]
      (get entity deprecated-attr)
      (conj [e :deprecated? true]))))

(defn all-idents-as-datoms [db deprecated-attr]
  (into [] (comp (remove (comp datomic-schema? :v))
                 (mapcat (partial expand-ident-datom db deprecated-attr)))
        (d/datoms db :aevt :db/ident)))

(defn datascript-db [db-uri {:keys [::dd/deprecated-attr ::dd/multiple-databases?
                                    ::dd/uri-prefix]}]
  (cond-> {:db {:schema {:db/ident {:db/unique :db.unique/identity}}
                :datoms (all-idents-as-datoms (d/db (d/connect db-uri)) deprecated-attr)}}
    multiple-databases? (assoc :multiple-databases? true)))
