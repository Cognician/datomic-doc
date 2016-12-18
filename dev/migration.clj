(ns migration
  "Use the code below to quickly deprecate some of your idents.

  This code assumes that you have updated `dev/user.clj`'s `user/db-uri` var with your
  database uri.

  Alter `deprecated-attr` to set your own name for the attribute, fill in
  `attrs-to-deprecate` and/or `attr-nses-to-deprecate`, evaluate those forms and finally
  evaluate the two `d/transact` calls in the comment at the bottom."
  (:require [datomic.api :as d]
            user))

(def deprecated-attr :datomic-doc/deprecated)

(def deprecated-attr-tx
  [{:db/ident              deprecated-attr
    :db/valueType          :db.type/boolean
    :db/cardinality        :db.cardinality/one
    ;; these two are still necessary if you're on an older version of Datomic
    :db/id                 #db/id[:db.part/db]
    :db.install/_attribute :db.part/db}])

(def attrs-to-deprecate
  "Vector of keywords representing Datomic idents."
  [])

(def attr-nses-to-deprecate
  "Vector of strings representing namespaces of Datomic idents."
  [])

(def conn (partial d/connect user/db-uri))
(def db (comp d/db conn))

(comment

  @(d/transact (conn) deprecated-attr-tx)

  @(d/transact (conn)
               (for [attr (concat attrs-to-deprecate
                                  (d/q '[:find [?e ...] :in $ [?ns ...] :where
                                         [?e :db/ident ?i]
                                         [(namespace ?i) ?ns]]
                                       (db) attr-nses-to-deprecate))]
                 [:db/add attr deprecated-attr true]))

  _)
