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

## add the Catalog of life taxonomy of primates
${treemachine_invocation} inittax  example/col_primates.txt col test.db || exit

# add the NCBI taxonomy of primates
${treemachine_invocation} addtax  example/ncbi_primates.txt ncbi  test.db -1 || exit

# initialize the GoL - this will add the NCBI taxonomy as a tree to the GoL
${treemachine_invocation} inittree test.db
java  "-Dlog4j.configuration=debuglog4j.properties" -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addtree example/sarich_wilson_1967.tre Catarrhini sarichwilson1967 test.db
java  "-Dlog4j.configuration=debuglog4j.properties" -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addtree example/grehan_schwartz_2009.tre Hominoidea grehanschwartz2009 test.db
