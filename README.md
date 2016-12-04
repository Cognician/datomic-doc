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
(require 'cognician.datomic-doc.ring)

(def handler
  (-> routes
      (cognician.datomic-doc.ring/wrap-datomic-doc 
       {:datomic-uri "datomic:free://dd"
        :allow-write-fn (constantly true)}))
```

### Configuration map options:

**REQUIRED** `:datomic-uri` — A string with a valid Datomic database URI. 

For example:

```clojure
:datomic-uri "datomic:free://datomic-doc"
```



**OPTIONAL** `:allow-write-pred` — A function which takes the request and must return `true` if the active user may edit doc-strings. Users who pass this check automaticaly pass the check for `:allow-read-fn` (below). This enables the full editing UI.

For example:

```clojure
:allow-access-fn (fn [request] 
                   (contains? (get-in [:session :user :roles]) :admin))
```



**OPTIONAL** `:allow-read-pred` — A function which takes the request and must return `true` if the active user may access the UI, but not alter anything. This renders only the Markdown content with no editing tools. 

For example:

```clojure
:allow-access-fn (fn [request] 
                   (contains? (get-in [:session :user :roles]) :staff))
```



**Important note! If neither of the `:allow-*-pred` options is provided, the UI will not be available to anyone**.



**OPTIONAL** `:annotate-tx-fn` — A function which takes the request and a map and must return that map with any attr/value pairs that can be transacted. Typically used to annotate transactions with the enacting user.

For example:

```clojure
:annotate-tx-fn (fn [request tx-map] 
                  (assoc tx-map :transaction/altered-by 
                         [:user/email (get-in [:session :user :email])]))
```



**OPTIONAL** `:deprecated-attr` — A keyword which, when asserted on any entity with `:db/ident` with a truthy value, will exclude it from search results (unless optionally included), and cause the editor UI to display a deprecated notice. If not provided, the UI will not provide an option to include deprecated entities. 

For example:

```clojure
:deprecated-attr :cognician/deprecated
```


**OPTIONAL** `:uri-prefix` — A string declaring the initial part of all routes served by Datomic Doc. 

​
Default value is `"dd"`.

------

## Features

### Search

At `/dd`, a search UI:

- Find all schema and non-schema `:db/ident` entities that do _not_ have `<:deprecated-attr> true`. 
- Enable including deprecated item search with a checkbox.
- Simply links to permalink system.
- Examples:
  - `user` seeks `:user*`, `:user*/*` and `:*/user*` — general case: return matches of `(re-find (re-pattern ?input) (str attr))`.
  - `user/` seeks `:user/*` — optimised case: return only  `(= ?input (namespace attr))`.
  - `/email` seeks `:*/email*` — optimised case: return only `(re-find (re-pattern ?input) (name attr))`

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

### Type and identity

`Schema: :user/email` 

- The entity has both `:db/ident` and `:db.valueType`.
- `:db/ident` displayed.

`Enum: :status/active` 

- The entity has `:db/ident` but not `:db.valueType`.
- `:db/ident` displayed.

`Entity: :user/email "no@spam.thanks"` 

- The entity has no `:db/ident`.
- The full lookup ref from the route is displayed.

### Metadata

If the entity is schema or an enum value, show extra metadata:

- Schema only:
  - Type.
  - Cardinality.
  - Uniqueness.
  - Flags:
    - Indexed (only show if not also Unique).
    - No History.
    - Is Component.
    - Fulltext.
- Schema and enum values:
  - Deprecated — if deprecated, colour the UI red or amber somehow.
  - Stats:
    - Created timestamp.
    - Last asserted timestamp and timespan-ago.
    - Datom count.
  - A link to search for all other entities sharing this a namespace with this entity; i.e. a search for `<namespace>/`.

### Doc-string Editor

Edits the `:db/doc` string of whichever entity is loaded with a Markdown editor, using <https://github.com/tylingsoft/markdown-plus> — online demo: <http://mdp.tylingsoft.com/>. 

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

It is not a schema editor. It only manages `:db/doc` values, and its own `:datomic-doc/deprecated`

Use your REPL to add or modify schema and/or enums as per usual.
