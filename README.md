# Datomic Doc

Manage `:db/doc` string values for any addressable entity in a Datomic database.

Typically used to manage doc-strings for schema and enumeration values, but can be used to document arbitrary entities — if those entities have a valid unique string attribute.

------

## Installation & configuration

```clojure
(require 'cognician.datomic-doc.ring)

(def handler
  (-> routes
      (cognician.datomic-doc.ring/wrap-datomic-doc 
       {;; required
        :datomic-uri "datomic:free://dd"
        ;; optional
        :allow-access-fn (fn [request] (contains? (get-in [:session :user :roles]) :admin))
        :modified-by-user-fn (fn [request] (get-in request [:session :user :email])
        :deprecated-attr :cognician/deprecated
        :uri-prefix "datomic-doc"}))
```

Configuration map options:

- **REQUIRED** `:datomic-uri` — A string with a valid Datomic database URI. For example:

  ```clojure
  :datomic-uri "datomic:free://datomic-doc"
  ```

- **OPTIONAL** `:allow-access-fn` — A function which takes the request and must return `true` if the active user may access the UI. 
  Important note!: If this is not provided, the UI will be available to **EVERYONE**.
  For example:

  ```clojure
  :allow-access-fn (fn [request] (contains? (get-in [:session :user :roles]) :admin))
  ```

- **OPTIONAL** `:modified-by-user-fn` — A function which takes the request and must return a string that represents the active user somehow. If provided, all transactions will be annotated with `:datomic-doc/modified-by` and this value. For example:

  ```clojure
  :modified-by-user-fn (fn [request] (get-in request [:session :user :email])
  ```

- **OPTIONAL** `:deprecated-attr` — A keyword which, when asserted on any entity with `:db/ident` with a truthy value, will exclude it from search results (unless optionally included), and cause the editor UI to display a deprecated notice. For example:

  ```clojure
  :deprecated-attr :cognician/deprecated
  ```

- **OPTIONAL** `:uri-prefix` — A string declaring the initial part of all routes served by Datomic Doc. Default value is `"dd"`.

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

 `/dd/entity/user/email` ⟶ `[:user/email "no@spam.thanks"]`

### Editor

Edits the `:db/doc` string of whichever entity is loaded with a Markdown editor, using <https://github.com/tylingsoft/markdown-plus>. 

### Type and identity heading

#### Type classifier

If the entity is schema — that is, it has both `:db/ident` and `:db.valueType` — display **Schema** prefix.

If the entity is an enumeration value — that is, it has  `:db/ident` but not `:db.valueType — display **Enum** prefix.

Otherwise, display **Entity** prefix.

#### Identity

Display either the `:db/ident` value or the lookup ref attr and value.

#### Examples

- `Schema: :user/email` 
- `Enum: :status/active`
- `Entity: :user/email "robert@cognician.com"`

### Schema

When the entity is schema, show metadata:

- Deprecated — if deprecated, colour the UI red or amber somehow.
- Type.
- Cardinality.
- Uniqueness.
- Flags:
  - Indexed (only show if not also Unique).
  - No History.
  - Is Component.
  - Fulltext.
- Stats:
  - Created timestamp.
  - Last asserted timestamp and timespan-ago.
  - Datom count.
- A link to search for all schema sharing this attribute's namespace; i.e. a search for `<namespace>/`.

### Enum

When the entity is schema, show metadata:

- Deprecated — if deprecated, colour the UI red or amber somehow.
- Stats:
  - Created timestamp.
  - Last asserted timestamp and timespan-ago.
  - Datom count.
- A link to search for all enums sharing this enum's namespace; i.e. a search for `<namespace>/`.

------

## What Datomic Doc is NOT

It is not a schema editor. It only manages `:db/doc` values, and its own `:datomic-doc/deprecated`

Use your REPL to add or modify schema and/or enums as per usual.
