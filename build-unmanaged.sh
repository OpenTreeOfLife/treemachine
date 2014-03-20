#!/bin/bash

rm -rf unmanaged-classes
mkdir -p unmanaged-classes
javac -classpath target/treemachine-neo4j-plugins-0.0.1-SNAPSHOT.jar -d unmanaged-classes \
  src/main/java/opentree/plugins/Unmanaged.java
#  ~/Scratch/neo4j/community/server-examples/src/main/java/org/neo4j/examples/server/unmanaged/HelloWorldResource.java
(cd unmanaged-classes; jar -cf ../unmanaged.jar .)

# cp -p unmanaged.jar ../../neo4j-treemachine/lib/
