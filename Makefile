

all: compile restart test

PLUGIN=target/treemachine-neo4j-plugins-0.0.1-SNAPSHOT.jar
SETTINGS=host:apihost=http://localhost:7474 host:translate=true

compile: $(PLUGIN)

SOURCES=$(shell echo `find src -name "*.java"`)

$(PLUGIN): $(SOURCES)
	./mvn_serverplugins.sh -q

NEO=neo4j-1.9.5
neo4j: $(NEO)
$(NEO):
	curl http://files.opentreeoflife.org/neo4j/$(NEO).tar.gz >$(NEO).tar.gz
	tar xzf $(NEO).tar.gz

run: .running

.running: $(PLUGIN)
	rm -f .running
	$(NEO)/bin/neo4j stop
	cp -p $(PLUGIN) $(NEO)/plugins/
	$(NEO)/bin/neo4j start
	rm -f .not-running
	touch .running

test-v3: .running
	cd ws-tests; \
	for test in test_[^v]*.py; do \
	  echo $$test; \
	  python $$test $(SETTINGS); \
	done 

test-v2: .running
	cd ws-tests; \
	for test in test_v2*.py; do \
	  echo $$test; \
	  python $$test $(SETTINGS); \
	done

not-running: .not-running
.not-running:
	$(NEO)/bin/neo4j stop
	rm -f .running
	touch .not-running

test: test-v2 test-v3
