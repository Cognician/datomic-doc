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

(defn wrap-with-context [options handler]
  (fn [request]
    (-> (update request ::dd/context merge
                (-> options
                    options/maybe-prepare-database-uris
                    (select-keys
                                 [::dd/datomic-uri
                                  ::dd/datomic-uris
                                  ::dd/uri-prefix
                                  ::dd/deprecated-attr
                                  ::dd/annotate-tx-fn
                                  ::dd/multiple-databases?
                                  ::dd/js-to-load])))
        handler)))

(defn wrap-with-db [options handler]
  (fn [{:keys [::dd/context route-params] :as request}]
    (-> (assoc request :db-uri 
               (if-let [db-name (:db-name route-params)]
                  (get-in context [::dd/datomic-uris db-name])
                  (::dd/datomic-uri context)))
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
        (-> (update request ::dd/context merge
                    {:lookup-type lookup-type
                     :lookup-ref lookup-ref
                     :entity entity-map
                     :uri uri
                     :entity-stats (datomic/entity-stats db lookup-type lookup-ref)})
            handler)))))

(def entity-routes
  {:get 
   {""
    ::search
    
    #{["/" [#"ident"  :lookup-type] "/" :ns "/" :name]
      ["/" [#"ident"  :lookup-type]         "/" :name]
      ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" [#"[^/]+" :value]]
      ["/" [#"entity" :lookup-type]         "/" :name "/" [#"[^/]+" :value]]}
    {""      ::detail
     "/edit" ::edit}}})

(def database-entity-routes
  {"" ::database-list
   ["/" :db-name] entity-routes})

(defn make-routes [uri-prefix options]
  [uri-prefix
   (if (::dd/multiple-databases? options)
     database-entity-routes
     entity-routes)])

(defn make-route-handlers [options]
  (let [with-db (partial wrap-with-db options)
        with-db+entity (comp with-db (partial wrap-with-entity options))]
    {::database-list views/database-list
     ::search (with-db views/search)
     ::detail (with-db+entity views/detail)
     ::edit (with-db+entity views/edit)}))

(defn wrap-datomic-doc [handler options]
  (let [options (options/prepare-options options)
        {:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options
        dd-handler (->> (make-route-handlers options)
                        (bidi-ring/make-handler (make-routes uri-prefix options))
                        (wrap-with-context options))]
    (fn [{:keys [uri] :as request}]
      (or (when (string/starts-with? uri uri-prefix)
            (if (or (allow-write-pred request)
                    (allow-read-pred request))
              (dd-handler (assoc request ::dd/context
                                 {:read-only? (not (allow-write-pred request))}))
              {:status 403
               :headers {"Content-Type" "text/plain; charset=utf-8"}
               :body "Access denied"}))
          (when (string/starts-with? uri (str "/" views/asset-prefix))
            (response/resource-response uri))
          (handler request)))))
