ORIG="target/treemachine-neo4j-plugins-0.0.1-SNAPSHOT.jar"
TARGET="neo4j-server/plugins/treemachine-plugin.jar"
mv "$ORIG" "$TARGET"

# restart the server
neo4j-server/bin/neo4j restart