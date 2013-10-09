opentree-treemachine
===============
Installation
---------------
treemachine is managed by Maven v. 2 (including the dependencies). In order to compile and build treemachine, it is easiest to let Maven v. 2 do the hard work.

On Ubuntu you can install Maven v. 2 with:
sudo apt-get install maven2

Once Maven v. 2 is installed, you can 
	
	git clone git@github.com:OpenTreeOfLife/treemachine.git

then 
	
	sh mvn_cmdline.sh
	
This will compile a jar file in the target directory that has commands for constructing and synthesizing the graph from the command line. 

If you would rather use the neo4j server and the plugins that are written for interacting with the graph over REST calls, you will compile the server plugins. To compile and package what is necessary for the server plugins

	sh mvn_serverplugins.sh
	
The compilation of the server plugins will delete the treemachine jar in the target directory. You can rebuild either just by running those scripts again.

Usage
--------------
To see the help message run:

	java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar

See below for an example of adding information to a database. More examples are being added to the treemachine wiki https://github.com/OpenTreeOfLife/treemachine/wiki. 

### Quickstart
There is an example script that will load the ncbi taxonomy that is in the examples directory as well as two trees. It does this with two commands inittax and addnewick.

### Using the neo4j server
There are a number of ways to visualize the content in the database. One way to do so is with the neo4j server. You do not need the server to load content or run analyses. However, it does off one way of visualizing the database. This requires having the full neo4j installation from http://neo4j.org/download  Note that the file $(NEO4J_HOME)/conf/neo4j-server.properties will have to be modified slightly. Typically, you'll just have to put the full path of the db directory that you are using with the opentree-treemachine as the value for the org.neo4j.server.database.location setting.


After you have loaded content into your db, you can run the neo4j http server
with the command:

	neo4j start
	
### Taxonomy Loading
The taxonomy should have the format
uid	|	parent_uid	|	name	|	rank	|	sourceinfo	|	uniqname	|	flags	|	
It can have 1 header line and the white space is a single tab.

The code has been refactored to have only one taxonomy (the preferred taxonomy). Loading multiple taxonomies has been moved to taxomachine.

As an example of usage to load the snapshot of OTToL into test.db:

	wget https://bitbucket.org/blackrim/avatol-taxonomies/downloads/ottol_dumpv1_w_preottol_ids_uniqunames.tar.gz
	tar xf ottol_dumpv1_w_preottol_ids_uniqunames.tar.gz
	java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar inittax ottol_dump_w_uniquenames_preottol_ids ottol_dump.synonyms test.db

To load a tree:

	java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addtree ex.nexson rosids WangEtAl2009-studyid-15 test.db

An older description on loading the taxonomies for the full ToL at 
https://docs.google.com/document/d/1J82ZvgqMwv9Y43SqSGcw1ZjqWEPHaFQww5deuFFV7Js/edit

### Credits/Attribution
The (non-essential) program scripts/compare_normalized_original_names_in_taxonomy.py
uses a function from http://en.wikipedia.org/wiki/Levenshtein_distance That code is
released under the CC-SA (http://creativecommons.org/licenses/by-sa/3.0/)
