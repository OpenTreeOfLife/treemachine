[![Build Status](https://secure.travis-ci.org/OpenTreeOfLife/treemachine.png)](http://travis-ci.org/OpenTreeOfLife/treemachine)

# treemachine-LITE

## Description

treemachine-LITE is a pared-down version of the original treemachine which was used to generate synthetic phylogenetic
trees for the [Open Tree of Life project](http://opentreeoflife.org/). Synthetic analyses are now performed by other tools.
The role of treemachine-LITE is simply to construct a neo4j database which is used to serve such trees.

## Installing dependencies

treemachine-LITE is managed by Maven v.3 (including the dependencies). In order to compile and build treemachine-LITE, it is easiest to let Maven v.3 do the hard work.

**maven**
On Linux you can install Maven v.3 with:
```
sudo apt-get install maven
```
On Mac OS, Maven v.3 can be installed with [Homebrew](http://brew.sh):
```
brew install maven
```
**jade and ot-base**
Once Maven v.3 is installed, the treemachine-LITE dependencies themselves can be installed using the script 'mvn_install_dependencies.sh'

**neo4j**
The DB constructed by treemachine-lite is meant to be served by neo4j. We are currently using `neo4j-community-v1.9.5`. To obtain and decompress neo4j:

```
$ curl http://files.opentreeoflife.org/neo4j/neo4j-community-1.9.5-unix.tar.gz > neo4j-community-1.9.5.tar.gz
$ tar xzf neo4j-community-1.9.5.tar.gz
```

Alternately, there is a `make` target for neo4j:

```
make neo4j
```

You can move the neo4j directory wherever you like, but make sure that it is on your path.

## Compiling treemachine-LITE

NOTE: The script for compiling the server plugins will delete the treemachine-LITE jar in the target directory (and the opposite is true - compiling the jar in the target dir will delete the server plugins). You can rebuild either just by running those scripts again.

### Command-line version

To compile the command-line version (which you can then use to build a database):

```
sh mvn_cmdline.sh
```

This creates `treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar` in the target directory.

### Server plugins

To compile the server plugins for interacting with the graph over REST calls:

```
sh mvn_serverplugins.sh
```

## Usage

### Constructing a DB

First, compile the command-line version (see above). Then, to build the DB:

```
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar ingestsynth newick_tree json_annotations tsv_taxonomy DB_name
```

where:

* `newick_tree` is the `labelled_supetree/labelled_supertree.tre` from the synthesis procedure
* `json_annotations` is the `annotated_supertree/annotations.json` from the supertree procedure
* `tsv_taxonomy` is the `taxonomy.tsv` file from the [Open Tree of Life Taxonomy (OTT)](https://tree.opentreeoflife.org/about/taxonomy-version/)
* `DB_name` is the name for the generated DB

### Serving a DB with Neo4j

Compile the server plugins (see 'Server plugins, above). Before starting neo4j, file `$(NEO4J_HOME)/conf/neo4j-server.properties` will have to be modified slightly. Typically,
you'll just have to put the full path of the DB directory constructed by treemachine-LITE as the value for the
`org.neo4j.server.database.location` setting.

After you have loaded content into your db, you can run the neo4j http server with the command (assuming that the neo4j directory is on your path, otherwise you can directly call `bin/neo4j` in the neo4j directory):

```
neo4j start
```
### Running the tests

To make sure everything is running ok, run the web service tests:

```
cd ws-tests
./run_tests.sh host:apihost=http://localhost:7474 host:translate=true
```
