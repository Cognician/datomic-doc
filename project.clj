(defproject cognician/datomic-doc "0.1.0"
  :description "Markdown-based documentation editor for Datomic entities."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-future-spec "1.9.0-alpha14"]
                 [bidi "2.0.14" :exclusions [ring/ring-core]]
                 [com.datomic/datomic-free "0.9.5544" :scope "provided"]
                 [ring/ring-core "1.5.0" :exclusions [commons-codec]]
                 ;; dependencies are fun
                 [org.clojure/tools.reader "1.0.0-beta3"]]
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* false}
  :profiles {:dev 
             {:dependencies 
              [[proto-repl "0.3.1"]
               [http-kit "2.2.0"]
               [org.clojure/core.async "0.2.395"]
               [org.clojure/clojurescript "1.9.293"]
               [rum "0.10.7"]
               [datascript "0.15.5"]
               [figwheel-sidecar "0.5.8"]
               ;; dependencies are fun
               ;; datomic brings in guava 18.0, which is incompatible with cljs
               [com.google.guava/guava "19.0"]]
              :plugins [[lein-cljsbuild "1.1.5" :exclusions [org.clojure/clojure]]]
              :source-paths ["dev"]
              :resource-paths ["target/js"]}}
  :clean-targets ^{:protect false} ["target" "resources/cognician/datomic-doc/js"]
  :auto-clean false
  :aliases {"package"
            ["do"
             ["clean"]
             ["cljsbuild" "once" "production"]
             ["jar"]]}
  :cljsbuild {:builds
              [{:id "production"
                :source-paths ["src"]
                :compiler 
                {:optimizations :advanced
                 :output-to "resources/cognician/datomic-doc/js/main.min.js"
                 :output-dir "target/js/main/cognician/datomic-doc/main"
                 :compiler-stats true
                 :parallel-build true
                 :source-map "resources/cognician/datomic-doc/js/main.min.js.map"
                 :source-map-timestamp true
                 :externs ["externs/ace_externs.js"]}}]})
