#!/bin/sh
if test -d test.db
then
    echo "test.db is in the way!"
    exit 1
fi

if test -z "$TRACE_LEVEL"
then
    config_arg_val=debuglog4j.properties
else
    config_arg_val=tracelog4j.properties
fi

#compose the java command to invoke the treemachine
treemachine_invocation="java -Dlog4j.configuration=${config_arg_val} -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar"

# turn on command echoing
set -x

# added for the new refactor
${treemachine_invocation} inittax  example/ncbi_primates.txt test.db || exit

# initialize the GoL - this will add the NCBI taxonomy as a tree to the GoL
#${treemachine_invocation} inittree test.db
java  "-Dlog4j.configuration=debuglog4j.properties" -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addnewick example/sarich_wilson_1967.tre F Catarrhini sarichwilson1967 test.db
java  "-Dlog4j.configuration=debuglog4j.properties" -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addnewick example/grehan_schwartz_2009.tre F Hominoidea grehanschwartz2009 test.db
