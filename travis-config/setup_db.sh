# install graph commander repo
git clone http://github.com/OpenTreeOfLife/gcmdr.git
cd gcmdr

# run the asterales example
TREEMACHINE_JAR=$(pwd)'/target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar' && export TREEMACHINE_JAR
./run_asterales_example.py
cd ../

# download neo4j
wget "http://neo4j.com/artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
tar -xvf "artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
mv neo4j-community-1.9.8 neo4j-server

mv gcmdr/example/asterales_synth.db data/graph.db