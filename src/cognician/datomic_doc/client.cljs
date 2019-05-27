(ns ^:figwheel-always cognician.datomic-doc.client
  (:require [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.components.database-list :refer [database-list]]
            [cognician.datomic-doc.client.components.detail :refer [detail]]
            [cognician.datomic-doc.client.components.editor-buttons :refer [editor-buttons]]
            [cognician.datomic-doc.client.components.search :refer [search]]
            [cognician.datomic-doc.client.start :as start]))

(def component-type->fn
  {"database-list"  database-list
   "detail"         detail
   "editor-buttons" editor-buttons
   "search"         search})

(defn start-client! []
  (start/start-all-components! common/state component-type->fn))

(start-client!)
