(defproject cognician/datomic-doc "0.1.0"
  :description "Markdown-based documentation editor for Datomic entities, particularly schema."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-future-spec "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [rum "0.10.7"]
                 [com.cognitect/transit-clj "0.8.295"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.datomic/datomic-pro "0.9.5530"]]
  :plugins [[lein-cljsbuild "1.1.3" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.4-7" :exclusions [org.clojure/clojure]]]
  :aliases {"fw" ["figwheel"]}
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* false}
  :profiles {:dev {:dependencies [[proto-repl "0.3.1"]]
                   :source-paths ["dev"]
                   :resource-paths ["target/js"]}}
  :cljsbuild {:builds [{:id "none"
                        :source-paths ["src" "dev"]
                        :figwheel { :on-jsload "cognician.datomic-doc.gallery/refresh"}
                        :compiler {:main cognician.datomic-doc.gallery
                                   :compiler-stats true
                                   :parallel-build true
                                   :asset-path "/none"
                                   :output-to "target/js/main.js"
                                   :output-dir "target/js/none"
                                   :optimizations     :none
                                   :source-map-timestamp true}}]}
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "robert@cognician.com"
                                   :password "e8a515af-c1bb-45b8-acb5-fba4ab6723d6"}}
  :figwheel {:http-server-root "."
             :builds-to-start  ["none"]
             :server-ip "0.0.0.0"
             :server-port 4000
             :ring-handler "cognician.datomic-doc.dev-server/handler"
             :css-dirs ["resources/cognician/datomic-doc"]
             :repl false})
