(ns migration
  (:require [datomic.api :as d]
            user))

(defn transact! [tx]
  @(d/transact (user/conn) tx))

(def attrs-to-deprecate
  [:meta/description
   :meta/icon
   :meta/message-templates
   :meta/no-icon-border?
   :meta/price
   :meta/slug
   :meta/title
   :meta/vatable
   :transaction/responsible-user-uuid
   :transaction/user-uuid
   :transaction/corrected?
   :semaphore.message-transport/envisionme
   :semaphore.message-transport/mad-mimi
   :semaphore.message-transport/nojoshmo
   :semaphore.message-transport/postmark])

(def attr-nses-to-deprecate
  ["address"
   "aggregate"
   "bundle-statistics"
   "contact"
   "currency"
   "demo-template"
   "fuse"
   "fusebox"
   "hex"
   "invite"
   "legacy-resource-statistics"
   "license"
   "list"
   "memento"
   "memento.tag"
   "message"
   "message-template"
   "message.trigger"
   "order"
   "package"
   "promo-code"
   "promo-code.value-type"
   "repo"
   "resource-statistics"
   "schedule"
   "searchable"
   "semaphore-template"
   "setting"])

(comment

  (transact! [{:db/ident              :cognician/deprecated
               :db/valueType          :db.type/boolean
               :db/cardinality        :db.cardinality/one
               :db/id                 #db/id[:db.part/db]
               :db.install/_attribute :db.part/db}])

  (transact! (concat
              (for [s attrs-to-deprecate]
                [:db/add s :cognician/deprecated true])
              (for [s (d/q '[:find [?e ...] :in $ [?ns ...] :where
                             [?e :db/ident ?i]
                             [(namespace ?i) ?ns]]
                           (user/db) attr-nses-to-deprecate)]
                [:db/add s :cognician/deprecated true])))

  _)
