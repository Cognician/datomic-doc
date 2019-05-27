(ns cognician.datomic-doc.routes
  (:require [cognician.datomic-doc :as dd]))

(def entity-routes
  {""
   {:get :search}

   ["/search/" [#"[A-Za-z-/\.\d]+" :query]]
   {:get :search-with-query}

   ["/" [#"ident" :lookup-type] "/" :name]
   {""      {:get :ident-detail}
    "/edit" {:get :ident-edit}
    "/doc"  {:get :ident-doc}
    "/save" {:post :ident-save}}

   ["/" [#"ident" :lookup-type] "/" :ns "/" :name]
   {""      {:get :ident-detail-with-ns}
    "/edit" {:get :ident-edit-with-ns}
    "/doc"  {:get :ident-doc-with-ns}
    "/save" {:post :ident-save-with-ns}}

   ["/" [#"entity" :lookup-type] "/" :name "/" [#"[^/]+" :value]]
   {""      {:get :entity-detail}
    "/edit" {:get :entity-edit}
    "/doc"  {:get :entity-doc}
    "/save" {:post :entity-save}}

   ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" [#"[^/]+" :value]]
   {""      {:get :entity-detail-with-ns}
    "/edit" {:get :entity-edit-with-ns}
    "/doc"  {:get :entity-doc-with-ns}
    "/save" {:post :entity-save-with-ns}}})

(def database-entity-routes
  {"" {:get :database-list}
   ["/" :db-name] entity-routes})

(defn make-routes [uri-prefix multiple-databases?]
  [uri-prefix
   (if multiple-databases?
     database-entity-routes
     entity-routes)])
