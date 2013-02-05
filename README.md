opentree-treemachine
===============
Installation
---------------
treemachine is managed by Maven v. 2 (including the dependencies). In order to compile and build treemachine, it is easiest to let Maven v. 2 do the hard work.

On Ubuntu you can install Maven v. 2 with:
sudo apt-get install maven2

Once Maven v. 2 is installed, you can 
	
	git clone git@github.com:OpenTreeOfLife/opentree-treemachine.git

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

See below for an example of adding information to a database. More examples are being added to the treemachine wiki https://github.com/OpenTreeOfLife/opentree-treemachine/wiki. 

### Using the neo4j server
There are a number of ways to visualize the content in the database. One way to do so is with the neo4j server. You do not need the server to load content or run analyses. However, it does off one way of visualizing the database. This requires having the full neo4j installation from http://neo4j.org/download  Note that the file $(NEO4J_HOME)/conf/neo4j-server.properties will have to be modified slightly. Typically, you'll just have to put the full path of the db directory that you are using with the opentree-treemachine as the value for the org.neo4j.server.database.location setting.


After you have loaded content into your db, you can run the neo4j http server
with the command:

	neo4j start
	
### Taxonomy Loading
The code has been refactored to have only one taxonomy (the preferred taxonomy). Loading multiple taxonomies has been moved to taxomachine.

As an example of usage, you can load the Primate subset of these taxonomies 
in a test.db with:

	java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar inittax example/ncbi_primates.txt test.db

Full description on loading the taxonomies for the full ToL at 
https://docs.google.com/document/d/1J82ZvgqMwv9Y43SqSGcw1ZjqWEPHaFQww5deuFFV7Js/edit



