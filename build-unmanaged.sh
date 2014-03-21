#!/bin/bash

# How to install the unmanaged (GET) methods:
# 1. Build the server plugin jar (./mvn_serverplugins.sh)
# 2. Build the unmanaged plugin jar using this script
# 3. Copy the jar to the neo4j lib/ subdirectory  (do not link)
# 4. Edit the conf/neo4j-server.properties file to add the following line:
#    org.neo4j.server.thirdparty_jaxrs_classes=opentree.plugins=/v1

rm -rf unmanaged-classes
mkdir -p unmanaged-classes
javac -classpath target/treemachine-neo4j-plugins-0.0.1-SNAPSHOT.jar -d unmanaged-classes \
  src/main/java/opentree/plugins/Unmanaged.java
(cd unmanaged-classes; jar -cf ../unmanaged.jar .)

# cp -p unmanaged.jar ../../neo4j-treemachine/lib/
