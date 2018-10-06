SHELL := /bin/bash

.PHONY: all

all: figwheel

figwheel:
	clojure -A\:dev script/dev.clj

clean:
	rm -rf resources/cognician/datomic-doc/js target

compile:
	clojure -m cljs.main -co "{:source-map \"resources/cognician/datomic-doc/js/main.min.js.map\",:externs [\"externs/ace_externs.js\"]}" -d resources/cognician/datomic-doc/js/out -t browser -O advanced -o resources/cognician/datomic-doc/js/main.min.js -c cognician.datomic-doc.client

outdated:
	clojure -Aoutdated -a outdated
