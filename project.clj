(defproject cognician/datomic-doc "0.1.0"
  :description "Markdown-based documentation editor for Datomic entities."
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [bidi "2.1.6" :exclusions [ring/ring-core]]
                 [com.datomic/datomic-free "0.9.5697" :scope "provided"]
                 [ring/ring-core "1.7.1" :exclusions [commons-codec]]]
  :resource-paths ["resources"]
  :global-vars {*warn-on-reflection* false}
  :profiles
  {:dev
   {:dependencies
    [[http-kit "2.3.0"]
     [org.clojure/core.async "0.4.490"]
     [org.clojure/clojurescript "1.10.520"]
     [org.clojure/tools.namespace "0.2.11"]
     [rum "0.11.3"]
     [datascript "0.18.3"]
     [nrepl "0.6.0"]
     [cider/cider-nrepl "0.21.0"]
     ;; Datomic brings in guava 18.0, which is incompatible with cljs
     [com.google.guava/guava "25.1-jre"]]
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
  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src"]
     :compiler
     {:optimizations  :none
      :output-dir     "resources/cognician/datomic-doc/js/main-dev"
      :output-to      "resources/cognician/datomic-doc/js/main.js"
      :source-map     true
      :compiler-stats true
      :parallel-build true
      :main           cognician.datomic-doc.client
      :asset-path     "/cognician/datomic-doc/js/main-dev"}}
    {:id "production"
     :source-paths ["src"]
     :compiler
     {:optimizations        :advanced
      :output-dir           "resources/cognician/datomic-doc/js/main"
      :output-to            "resources/cognician/datomic-doc/js/main.min.js"
      :source-map           "resources/cognician/datomic-doc/js/main.min.js.map"
      :compiler-stats       true
      :parallel-build       true
      :source-map-timestamp true
      :externs              ["externs/ace_externs.js"]}}]})
