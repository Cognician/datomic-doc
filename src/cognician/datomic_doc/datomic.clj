(ns cognician.datomic-doc.datomic
  (:require [clojure.string :as string]
            [cognician.datomic-doc.util :as util]
            [datomic.api :as d]))

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

(defn pull-entity [db lookup-ref deprecated-attr]
  (let [entity (-> (d/pull db (cond-> pull-spec
                                deprecated-attr (conj deprecated-attr))
                           lookup-ref)
                   util/flatten-idents)]
    (cond-> entity
      (get entity deprecated-attr) (assoc :deprecated? true))))

(defn classify-ident [db lookup-ref]
  (let [entity (d/entity db lookup-ref)]
    (cond
      (:db/valueType entity)          :schema
      (:db.install/_partition entity) :partition
      (:db/fn entity)                 :function
      :else                           :enum)))

(defn tx->txInstant [db tx]
  (->> tx (d/entity db) :db/txInstant))

(defn entity-stats [db lookup-type lookup-ref]
  (let [history-db (d/history db)
        index      (case lookup-type
                     :partition          nil
                     :schema             :aevt
                     :enum               :vaet
                     (:function :entity) :eavt)]
    (cond-> {:created
             (->> (d/datoms history-db :eavt lookup-ref)
                  (transduce (map :tx) min Long/MAX_VALUE)
                  (tx->txInstant db))}
      (contains? #{:schema :enum :entity :function} lookup-type)
      (assoc :last-touched
             (->> (d/datoms history-db index lookup-ref)
                  (transduce (map :tx) max 0)
                  (tx->txInstant db)))
      (contains? #{:schema :enum :entity} lookup-type)
      (assoc :datom-count
             (-> (seq (d/datoms db index lookup-ref))
                 seq
                 count)))))

(comment
  (entity-stats (user/db) :schema :meta/no-icon-border?)

  (time (entity-stats (user/db) :function :find-or-create-tag))
  (time (entity-stats (user/db) :partition :cognician))
  (time (entity-stats (user/db) :schema :user/status))
  (time (entity-stats (user/db) :enum :status/active))
  (time (entity-stats (user/db) :schema :navigation-event/url))
  (time (entity-stats (user/db) :schema :chat/event))
  (time (entity-stats (user/db) :enum :chat.event.type/change-response))
  (time (entity-stats (user/db) :entity [:user/email "barry@cognician.com"]))
  _)

(defn datomic-schema? [attr]
  (let [ns (str (namespace attr))]
    (or (string/starts-with? ns "db")
        (string/starts-with? ns "fressian"))))

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
