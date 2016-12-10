(defproject cognician/datomic-doc "0.1.0"
  :description "Markdown-based documentation editor for Datomic entities, particularly schema."
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.async "0.2.395"]
                 ;;[clojure-future-spec "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [http-kit "2.2.0"]
                 [bidi "2.0.14"]
                 [rum "0.10.7"]
                 [datascript "0.15.5"]
                 [com.cognitect/transit-clj "0.8.297"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.datomic/datomic-free "0.9.5544"]
                 [ring/ring-core "1.5.0"]
                 ;; sigh
                 [com.google.guava/guava "19.0"]
                 [commons-codec "1.10"]]
  :plugins [[lein-cljsbuild "1.1.5" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.8" :exclusions [org.clojure/clojure]]]
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* false}
  :profiles {:dev {:dependencies [[proto-repl "0.3.1"]
                                  [http-kit "2.2.0"]
                                  [com.taoensso/tufte "1.1.0"]]
                   :source-paths ["dev"]
                   :resource-paths ["target/js"]}}
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src" "dev"]
                        :figwheel {:on-jsload "cognician.datomic-doc.gallery/refresh"}
                        :compiler {:main cognician.datomic-doc.gallery
                                   :compiler-stats true
                                   :parallel-build true
                                   :asset-path "/dev"
                                   :output-to "target/js/main.js"
                                   :output-dir "target/js/dev"
                                   :optimizations :none
                                   :source-map-timestamp true}}
                       {:id "main"
                        :source-paths ["src"]
                        :compiler {:compiler-stats true
                                   :parallel-build true
                                   :asset-path "/main"
                                   :output-to "target/js/main.min.js"
                                   :output-dir "target/js/main"
                                   :optimizations :advanced
                                   :source-map-timestamp true}}]}
  :figwheel {:http-server-root "."
             :builds-to-start  ["dev"]
             :server-ip "0.0.0.0"
             :server-port 4000
             :css-dirs ["resources/cognician/datomic-doc"]
             :repl false})
