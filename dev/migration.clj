(ns migration
  "Use the code below to quickly deprecate some of your idents.

  This code assumes that you have updated `db-uri` var with your
  database uri.

  Alter `deprecated-attr` to set your own name for the attribute, fill in
  `attrs-to-deprecate` and/or `attr-nses-to-deprecate`, evaluate those forms and finally
  evaluate the two `d/transact` calls in the comment at the bottom."
  (:require [datomic.api :as d]
            user))

(def db-uri
  "Fill in your own database uri here, or if you just want to demo,
  follow the steps at https://github.com/Datomic/mbrainz-sample#getting-the-data
  to download a data set."
  "datomic:free://localhost:4334/*")

(def deprecated-attr :datomic-doc/deprecated)

(def deprecated-attr-tx
  [{:db/ident       deprecated-attr
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one}])

(def attrs-to-deprecate
  "Vector of keywords representing Datomic idents."
  [])

(def attr-nses-to-deprecate
  "Vector of strings representing namespaces of Datomic idents."
  [])

(def conn (partial d/connect db-uri))
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

  (let [dep (d/q '[:find (count ?e) . :in $ ?deprecate-attr :where
                   [?e :db/ident]
                   [?e ?deprecate-attr]]
                 (db) deprecated-attr)

        not-dep (d/q '[:find (count ?e) . :in $ ?deprecate-attr :where
                       [?e :db/ident]
                       (not [?e ?deprecate-attr])]
                     (db) deprecated-attr)]
    {:deprecated       dep
     :active           not-dep
     :deprecated-ratio (str (Math/round (* 100 (float (/ dep (+ dep not-dep))))) "%")})

  )
