CLASSPATH="/home/cody/phylo/treemachine/src/main/java/:/home/cody/.m2/repository/org/neo4j/1.9.M05/neo4j-1.9.M05.jar:/home/cody/.m2/repository/org/neo4j/neo4j-kernel/1.9.M05/neo4j-kernel-1.9.M05.jar:/home/cody/.m2/repository/org/neo4j/neo4j-graph-algo/1.9.M05/neo4j-graph-algo-1.9.M05.jar:/home/cody/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar:/home/cody/.m2/repository/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar"

echo compiling
javac -cp  $CLASSPATH src/main/java/opentree/GraphImporter.java

echo running
java -cp $CLASSPATH opentree.GraphImporter
