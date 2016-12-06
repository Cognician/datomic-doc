(ns ^:figwheel-always cognician.datomic-doc.client
  (:require [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.components.editor :refer [editor]]
            [cognician.datomic-doc.client.components.search :refer [search]]
            [cognician.datomic-doc.client.start :as start]))

(def component-type->fn
  {"editor" editor
   "search" search})

(defn start-client! []
  (start/start-all-components! common/state component-type->fn))

(start-client!)
