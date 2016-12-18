(ns cognician.datomic-doc.ring
  (:require [bidi.ring :as bidi-ring]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic :refer [as-db]]
            [cognician.datomic-doc.options :as options]
            [cognician.datomic-doc.views :as views]
            [ring.util.response :as response]))

(defn wrap-with-options+routes [options routes handler]
  (fn [request]
    (-> (merge request
               {:options (options/maybe-prepare-database-uris options)
                :routes  routes})
        handler)))

(defn wrap-with-db-uri [handler]
  (fn [{:keys [options route-params] :as request}]
    (-> (assoc request :db-uri
               (if-let [db-name (:db-name route-params)]
                 (get-in options [::dd/datomic-uris db-name])
                 (::dd/datomic-uri options)))
        handler)))

(defn wrap-with-entity [options handler]
  (fn [{{:keys [lookup-type ns name value]} :route-params db-uri :db-uri :as request}]
    (let [lookup-type (keyword lookup-type)
          name        (string/replace name "__Q" "?")
          lookup-attr (if ns
                        (keyword ns name)
                        (keyword name))
          lookup-ref  (case lookup-type
                        :ident  lookup-attr
                        :entity [lookup-attr value])
          db          (as-db db-uri)
          lookup-type (case lookup-type
                        :ident  (datomic/classify-ident db lookup-ref)
                        :entity :entity)
          entity-map  (datomic/pull-entity db lookup-ref (::dd/deprecated-attr options))]
      (if (= entity-map {:db/id nil})
        (response/not-found "That entity does not exist.")
        (-> (update request :entity merge
                    {:lookup-type  lookup-type
                     :lookup-ref   lookup-ref
                     :entity       entity-map
                     :entity-stats (datomic/entity-stats db lookup-type lookup-ref
                                                         (::dd/count-datoms? options))})
            handler)))))

(defn wrap-check-access [{:keys [::dd/uri-prefix
                                 ::dd/allow-read-pred
                                 ::dd/allow-write-pred]} handler]
  (fn [request]
    (or (when (string/starts-with? (:uri request) uri-prefix)
          (if (or (allow-write-pred request)
                  (allow-read-pred request))
            (handler (assoc request :read-only? (not (allow-write-pred request))))
            views/access-denied-response))
        (handler request))))

(def entity-routes
  {:get
   {""
    :search

    ["/search/" [#"[A-Za-z-/\.\d]+" :query]]
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
  {""             :database-list
   ["/" :db-name] entity-routes})

(defn make-routes [uri-prefix options]
  [uri-prefix
   (if (::dd/multiple-databases? options)
     database-entity-routes
     entity-routes)])

(defn make-route-handlers [options]
  (let [check-access  (partial wrap-check-access options)
        search        (-> views/search check-access wrap-with-db-uri)
        db-uri+entity (comp wrap-with-db-uri
                            (partial wrap-with-entity options)
                            check-access)
        detail        (db-uri+entity views/detail)
        edit          (db-uri+entity views/edit)]
    {:database-list         (check-access views/database-list)
     :search                search
     :search-with-query     search
     :ident-detail          detail
     :ident-detail-with-ns  detail
     :ident-edit            edit
     :ident-edit-with-ns    edit
     :entity-detail         detail
     :entity-detail-with-ns detail
     :entity-edit           edit
     :entity-edit-with-ns   edit}))

(defn wrap-datomic-doc [handler options]
  (let [options    (options/prepare-options options)
        uri-prefix (::dd/uri-prefix options)
        routes     (make-routes uri-prefix options)
        dd-handler (->> (make-route-handlers options)
                        (bidi-ring/make-handler routes)
                        (wrap-with-options+routes options routes))
        asset-path (str "/" views/asset-prefix)]
    (fn [{:keys [uri] :as request}]
      (or (when (string/starts-with? uri uri-prefix)
            (dd-handler request))
          (when (string/starts-with? uri asset-path)
            (response/resource-response uri))
          (handler request)))))
