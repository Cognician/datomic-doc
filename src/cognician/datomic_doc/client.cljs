(ns ^:figwheel-always cognician.datomic-doc.client
  (:require [cognician.datomic-doc.client.common :as common]
            [cognician.datomic-doc.client.components.detail :refer [detail]]
            [cognician.datomic-doc.client.components.search :refer [search]]
            [cognician.datomic-doc.client.start :as start]))

(def component-type->fn
  {"detail" detail
   "search" search})

(defn start-client! []
  (start/start-all-components! common/state component-type->fn))

(start-client!)
