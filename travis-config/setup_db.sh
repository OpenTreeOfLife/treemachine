# first record the location of the treemachine standalone jar
TREEMACHINE_JAR=$(pwd)'/target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar' && export TREEMACHINE_JAR

# now install graph commander repo and run the asterales example
git clone http://github.com/OpenTreeOfLife/gcmdr.git
cd gcmdr
python run_asterales_example.py
cd ../

# download neo4j
wget "http://neo4j.com/artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
tar -xvf "artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
mv neo4j-community-1.9.8 neo4j-server

# move the asterales example to the default neo4j db location
mv gcmdr/example/asterales_synth.db neo4j-server/data/graph.db