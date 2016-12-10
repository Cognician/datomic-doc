(ns cognician.datomic-doc.ring
  (:require [bidi.ring :as bidi-ring]
            [clojure.string :as string]
            [cognician.datomic-doc :as dd]
            [cognician.datomic-doc.datomic :as datomic]
            [cognician.datomic-doc.options :as options]
            [cognician.datomic-doc.views :as views]
            [datomic.api :as d]
            [ring.util.response :as response]))

(defn wrap-with-context [options handler]
  (fn [request]
    (-> (update request ::dd/context merge
                (let [conn (d/connect (::dd/datomic-uri options))]
                  {:conn    conn
                   :db      (d/db conn)
                   :options (select-keys options [::dd/uri-prefix
                                                  ::dd/deprecated-attr
                                                  ::dd/annotate-tx-fn])}))
        handler)))

(defn wrap-with-entity [options handler]
  (fn [{{:keys [lookup-type ns name value]} :route-params :as request}]
    (let [lookup-type (keyword lookup-type)
          name        (string/replace name "__Q" "?")
          lookup-attr (if ns
                        (keyword ns name)
                        (keyword name))
          lookup-ref  (case lookup-type
                        :ident  lookup-attr
                        :entity [lookup-attr value])
          db          (get-in request [::dd/context :db])
          lookup-type (case lookup-type
                        :ident  (datomic/classify-ident db lookup-ref)
                        :entity :entity)
          entity-map  (datomic/pull-entity db lookup-ref (::dd/deprecated-attr options))]
      (if (= entity-map {:db/id nil})
        (response/not-found "That entity does not exist.")
        (-> (update request ::dd/context merge
                    {:lookup-type  lookup-type
                     :lookup-ref   lookup-ref
                     :entity       entity-map
                     :entity-stats (datomic/entity-stats db lookup-type lookup-ref)})
            handler)))))

(defn make-routes [uri-prefix options]
  [uri-prefix
   {:get {"" views/search
          #{["/" [#"ident"  :lookup-type] "/" :ns "/" :name]
            ["/" [#"ident"  :lookup-type]         "/" :name]
            ["/" [#"entity" :lookup-type] "/" :ns "/" :name "/" [#"[^/]+" :value]]
            ["/" [#"entity" :lookup-type]         "/" :name "/" [#"[^/]+" :value]]}
          (bidi-ring/wrap-middleware views/editor (partial wrap-with-entity options))}}])

(defn wrap-datomic-doc [handler options]
  (let [options                         (options/prepare-options options)
        {:keys [::dd/uri-prefix
                ::dd/allow-read-pred
                ::dd/allow-write-pred]} options
        dd-handler                      (wrap-with-context options
                                                           (-> uri-prefix
                                                               (make-routes options)
                                                               bidi-ring/make-handler))]
    (fn [request]
      (or (when (string/starts-with? (:uri request) uri-prefix)
            (if (or (allow-write-pred request)
                    (allow-read-pred request))
              (dd-handler (assoc request ::dd/context
                                 {:read-only? (not (allow-write-pred request))}))
              {:status  403
               :headers {"Content-Type" "text/plain; charset=utf-8"}
               :body    "Access denied"}))
          (handler request)))))
