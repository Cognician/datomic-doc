SHELL := /bin/bash

.PHONY: all

all: figwheel

figwheel:
	clojure -A\:dev script/dev.clj

outdated:
	clojure -Aoutdated -a outdated

clean:
	rm -rf resources/cognician/datomic-doc/js target

compile:
	clojure -m cljs.main -co "{:source-map \"resources/cognician/datomic-doc/js/main.min.js.map\",:externs [\"externs/ace_externs.js\"]}" -d resources/cognician/datomic-doc/js/out -t browser -O advanced -o resources/cognician/datomic-doc/js/main.min.js -c cognician.datomic-doc.client

pre-pack:
	rm -rf resources/cognician/datomic-doc/js/out
	rm -rf target/datomic-doc.jar

datomic-doc.jar: deps.edn src/**/*
	clojure -Sdeps '{:deps {pack/pack.alpha {:git/url "https://github.com/juxt/pack.alpha.git" :sha "d9023b24c3d589ba6ebc66c5a25c0826ed28ead5"}}}' -m mach.pack.alpha.skinny --no-libs --project-path "target/datomic-doc.jar"

pom.xml: deps.edn
	clojure -Spom

clojars:
	mvn deploy:deploy-file -Dfile=target/datomic-doc.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

deploy: clean compile pre-pack datomic-doc.jar pom.xml clojars
	echo "done"