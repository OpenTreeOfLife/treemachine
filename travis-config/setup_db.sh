########

# here we should clone gcmdr and run the asterales example, then install that db into the neo4-server

########

# download and set up ott
#wget "http://files.opentreeoflife.org/ott/aster.tgz"
#tar -xvf aster.tgz

#T="aster/taxonomy.tsv" 
#S="aster/synonyms.tsv"
#D="aster/deprecated.tsv" 

JAR="target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar"
#CMD="java -jar $JAR "
#DB=$(pwd)"/aster.db"

#$CMD loadtaxsyn "aster" $T $S $DB
#$CMD makecontexts $DB
#$CMD makegenusindexes $DB
#$CMD adddeprecated $D $DB

# download neo4j
wget "http://neo4j.com/artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
tar -xvf "artifact.php?name=neo4j-community-1.9.8-unix.tar.gz"
mv neo4j-community-1.9.8 neo4j-server

# point the server at the taxomachine db location
#LINE="org.neo4j.server.database.location=$DB"
#NEW="neo4j-server/conf/neo4j-server.properties"
#ORIG="$NEW.original"
#mv "$NEW" "$ORIG"
#printf "$LINE\n\n" > "$NEW"
#grep -v "org.neo4j.server.database.location" "$ORIG" >> "$NEW"