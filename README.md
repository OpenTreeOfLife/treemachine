[![Build Status](https://secure.travis-ci.org/OpenTreeOfLife/treemachine.png)](http://travis-ci.org/OpenTreeOfLife/treemachine)

treemachine-LITE
===============
Description
---------------
treemachine-LITE is a pared-down version of the original treemachine which was used to generate synthetic phylogenetic 
trees for the [Open Tree of Life project](http://opentreeoflife.org/). Synthetic analyses are now performed by other tools. 
The role of treemachine-LITE is simply to construct a neo4j database which is used to serve such trees.

Installation
---------------
### Dependencies
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
The DB constructed by treemachine-lite is meant to be server by neo4j. We are currently using v1.9.5. This can be 
found in the `deps` directory of this repo. Simply decompress the archive:
```
tar -xvzf neo4j-community-1.9.5.tgz
```
whereever you like. Make sure that this directory lies in your path.

### treemachine-LITE
With all of the dependencies installed, we are free to acquire and compile treemachine-LITE itself. Navigate to where you would like to put treemachine-LITE, and execute the following:
```
git clone git@github.com:OpenTreeOfLife/treemachine.git
cd treemachine
git checkout tm-lite
```	
This will compile a jar file in the target directory that has commands for constructing the graph from the command line 
(see below). 

To compile the server plugins for interacting with the graph over REST calls, do:
```
sh mvn_serverplugins.sh
```
NOTE: the compilation of the server plugins will delete the treemachine-LITE jar in the target directory. You can rebuild either just by running those scripts again.

Usage
--------------
### Constructing a DB
Constructing a DB is accomplished by the following. First, compile the jar file in the target directory that has commands 
for constructing the graph:
```
sh mvn_cmdline.sh
```
To build the DB, type:

```
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar ingestsynth newick_tree json_annotations tsv_taxonomy DB_name
```
where `newick_tree` and `json_annotations` are outputs from the synthesis procedure, `tsv_taxonomy` is the taxonomy.tsv 
file from the [Open Tree of Life Taxonomy (OTT)](https://tree.opentreeoflife.org/about/taxonomy-version/), and 
`DB_name` is the name for the generated DB.

### Serving a DB with Neo4j
To compile the server plugins for interacting with the graph over 
[REST calls](https://github.com/OpenTreeOfLife/opentree/wiki/Open-Tree-of-Life-APIs-v3), do:
```
sh mvn_serverplugins.sh
```
NOTE: the compilation of the server plugins will delete the treemachine-LITE jar in the target directory. You can rebuild either just by running those scripts again.

Before starting neo4j, file $(NEO4J_HOME)/conf/neo4j-server.properties will have to be modified slightly. Typically, 
you'll just have to put the full path of the DB directory constructed by treemachine-LITE as the value for the 
`org.neo4j.server.database.location` setting.

After you have loaded content into your db, you can run the neo4j http server with the command:
```
neo4j start
```

