(ns cognician.datomic-doc.ring
  (:require [bidi.ring :as bidi-ring]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic :refer [as-conn as-db]]
            [cognician.datomic-doc.options :as options]
            [cognician.datomic-doc.views :as views]
            [datomic.api :as d]
            [ring.middleware.resource :as resource]
            [ring.util.response :as response]))

(defn wrap-with-context [options routes handler]
  (fn [request]
    (-> (merge request
               {:routes routes
                :options (-> (options/maybe-prepare-database-uris options)
                             (dissoc ::dd/allow-write-pred ::dd/allow-read-pred))})
        handler)))

(defn wrap-with-db [handler]
  (fn [{:keys [options route-params] :as request}]
    (-> (assoc request :db-uri 
               (if-let [db-name (:db-name route-params)]
                  (get-in options [::dd/datomic-uris db-name])
                  (::dd/datomic-uri options)))
        handler)))

(defn wrap-with-entity [options handler]
  (fn [{{:keys [lookup-type ns name value]} :route-params 
        uri :uri 
        db-uri :db-uri 
        :as request}]
    (let [lookup-type (keyword lookup-type)
          name (string/replace name "__Q" "?")
          lookup-attr (if ns
                        (keyword ns name)
                        (keyword name))
          lookup-ref  (case lookup-type
                        :ident lookup-attr
                        :entity [lookup-attr value])
          db (as-db db-uri)
          lookup-type (case lookup-type
                        :ident (datomic/classify-ident db lookup-ref)
                        :entity :entity)
          entity-map (datomic/pull-entity db lookup-ref (::dd/deprecated-attr options))]
      (if (= entity-map {:db/id nil})
        (response/not-found "That entity does not exist.")
        (-> (update request :context merge
                    {:lookup-type lookup-type
                     :lookup-ref lookup-ref
                     :entity entity-map
                     :uri uri
                     :entity-stats (datomic/entity-stats db lookup-type lookup-ref
                                                         (::dd/count-datoms? options))})
            handler)))))

(def entity-routes
  {:get 
   {""
    :search
    
    ["/search/" [#"[a-z-/\.\d]+" :query]]
    :search-with-query
     
    ["/" [#"ident" :lookup-type] "/" :name]
    {""      :ident-detail
     "/edit" :ident-edit}
      
    ["/" [#"ident" :lookup-type] "/" :ns "/" :name]
    {""      :ident-detail-with-ns
     "/edit" :ident-edit-with-ns}
      
    ["/" [#"entity" :lookup-type] "/" :name "/" [#"[^/]+" :value]]
    {""      :entity-detail
     "/edit" :entity-edit}
      
    ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" [#"[^/]+" :value]]
    {""      :entity-detail-with-ns
     "/edit" :entity-edit-with-ns}}})

(def database-entity-routes
  {"" :database-list
   ["/" :db-name] entity-routes})

(defn make-routes [uri-prefix options]
  [uri-prefix
   (if (::dd/multiple-databases? options)
     database-entity-routes
     entity-routes)])

(defn make-route-handlers [options]
  (let [wrap-with-db+entity (comp wrap-with-db (partial wrap-with-entity options))
        search (wrap-with-db views/search)
        detail (wrap-with-db+entity views/detail)
        edit (wrap-with-db+entity views/edit)]
    {:database-list views/database-list
     :search search
     :search-with-query search
     :ident-detail detail
     :ident-edit edit
     :entity-detail detail
     :entity-edit edit
     :ident-detail-with-ns detail
     :ident-edit-with-ns edit
     :entity-detail-with-ns detail
     :entity-edit-with-ns edit}))

(defn wrap-datomic-doc [handler options]
  (let [options (options/prepare-options options)
        {:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options
        routes (make-routes uri-prefix options)
        dd-handler (->> (make-route-handlers options)
                        (bidi-ring/make-handler routes)
                        (wrap-with-context options routes))]
    (fn [{:keys [uri] :as request}]
      (or (when (string/starts-with? uri uri-prefix)
            (if (or (allow-write-pred request)
                    (allow-read-pred request))
              (dd-handler (assoc request :context
                                 {:read-only? (not (allow-write-pred request))}))
              views/access-denied-response))
          (when (string/starts-with? uri (str "/" views/asset-prefix))
            (response/resource-response uri))
          (handler request)))))
