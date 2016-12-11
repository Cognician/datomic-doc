# Datomic Doc

Manage `:db/doc` string values for any addressable entity in a Datomic database.

Typically used to manage doc-strings for schema and enumeration values, but can be used to document arbitrary entities — if those entities have a valid unique string attribute.

------

## Installation & configuration

Leiningen coordinates:

```clojure
[cognician/datomic-doc "0.1.0"]
```

Integration with your web service handler, using sensible "getting started" configuration:

```clojure
(require '[cognician.datomic-doc :as dd] 
         '[cognician.datomic-doc.ring :as ddr])

(def handler
  (-> routes
      (ddr/wrap-datomic-doc 
       {::dd/datomic-uri "datomic:free://dd"
        ::dd/allow-write-fn (constantly true)}))
```

### Configuration map options

Key | Type | Use
---|---|---
`::dd/datomic-uri` | String | **Required.** Valid Datomic database URI.
`::dd/allow-write-pred` | Predicate function | Enables the full editing UI for the active user. A function which takes the request and must return `true` if the active user may edit doc-strings. Users who pass this check automatically pass the check for `::dd/allow-read-pred`.
`::dd/allow-read-pred` | Predicate function | Enables read-only UI for the active user. A function which takes the request and must return `true` if the active user may access the UI, but not alter anything.
`::dd/annotate-tx-fn` | Function | Allows for Datomic Doc's transactions to be annotated. A function which takes the request and a map and must return that map with any attr/value pairs that should be transacted on the inbound transaction.
`::dd/deprecated-attr` | Keyword | When asserted on any entity with `:db/ident` with a truthy value, will cause the UI to display a "Deprecated" notice for that entity. Also, fully deprecated _namespaces_ (where all attributes in a namespace are deprecated) will be listed separately in the namespace list.
`::dd/uri-prefix` | String | The first segment of all routes served by Datomic Doc. Can't contain any `"/"` characters. Default value is `"dd"`.

**Important note! If neither of the `::dd/allow-*-pred` options is provided, the UI will not be available to anyone**.

### Full example configuration

```clojure
{::dd/datomic-uri      "datomic:free://datomic-doc"
 ::dd/allow-write-pred (fn [request]
                         (contains? (get-in request [:user :roles]) :admin))
 ::dd/allow-read-pred  (fn [request]
                         (contains? (get-in request [:user :roles]) :staff))
 ::dd/annotate-tx-fn   (fn [request tx-map] 
                         (assoc tx-map :transaction/altered-by 
                                [:user/email (get-in request [:user :email])]))
 ::dd/deprecated-attr  :datomic-doc/deprecated
 ::dd/uri-prefix       "datomic-doc"}
```

------

## Features

### Search

At `/dd`, a search UI, which searches all `:db/ident` values - schema, enums, partitions and database functions (Datomic's own idents are excluded). Results lead to [permalinks](#permalinks).

#### Examples:

Query | Search result | Examples
---|---|---
`user` | Presence of query in namespace and name of ident. | `:user/email`, `:group/users`
`user/` | Exact match of query to namespace of ident. | `:user/email`, `:user/password`
`/user` | Presence of query in name of ident. | `:group/users`, `:license/max-user-count`

Searches can be pre-filled via a query string parameter `query`, e.g. `/dd?query=user`.

### Permalinks

#### Idents

`/dd/ident/:name` or `/dd/ident/:namespace/:name` 

⟶ Entity by direct `:db/ident` lookup with: `:<[namespace/]name>`.

##### Examples

 `/dd/ident/unused` ⟶ `:unused` 

 `/dd/ident/db/doc` ⟶ `:db/doc`

#### Entities

`/dd/entity/:name/:value` or `/dd/entity/:namespace/:name/:value` 

⟶ Entity by lookup ref lookup with: `[:<[namespace/]name> <value>]`. 
This requires that the attr be `:db/unique` and that the value be of `:db/valueType` `:db.valueType/string`.

##### Examples

 `/dd/entity/tag/value` ⟶ `[:tag "value"]` 

 `/dd/entity/user/email/no@spam.thanks` ⟶ `[:user/email "no@spam.thanks"]`

**Note:** Question mark characters are represented as `__Q` in permalinks: 
- e.g. `:user/opt-in?` ⟶ `/dd/ident/user/opt-in__Q`.

### Metadata

#### All entities

- Created timestamp.
- Last used timestamp.
- Usage count (by count of datoms).

#### Schema only

- Type.
- Cardinality.
- Uniqueness.
- Flags:
  - Indexed (only show if not also Unique).
  - No History.
  - Is Component.
  - Fulltext.

#### Schema and enum values only

- Deprecated — if deprecated, indicate this.
- A link to search for all other entities sharing this a namespace with this entity; i.e. a search for `<namespace>/`.

### Doc-string Editor

Append `/edit` to any [permalink](#permalinks), e.g. `/dd/ident/db/doc/edit`.

Loads the entity's `:db/doc` string into the <https://github.com/tylingsoft/markdown-plus> Markdown editor — online demo: <http://mdp.tylingsoft.com/>. 

#### Notable features include:

- Real-time HTML preview, with scroll lock
- Clojure syntax highlighting
- [Github-flavoured markdown](https://help.github.com/articles/github-flavored-markdown/)
- [Table of contents](http://mdp.tylingsoft.com/#table-of-contents)
- [Flowcharts](http://mdp.tylingsoft.com/#flowchart)
- [Sequence diagrams](http://mdp.tylingsoft.com/#sequence-diagram)
- [Charts](http://mdp.tylingsoft.com/#charts)

------

## What Datomic Doc is NOT

It is not a schema editor. It only manages `:db/doc` values, and its own `::dd/deprecated`

Use your REPL to add or modify schema and/or enums as per usual.
