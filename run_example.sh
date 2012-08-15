#!/bin/sh
if test -d test.db
then
    echo "test.db is in the way!"
    exit 1
fi
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar inittax  example/col_primates.txt col test.db || exit
if test -z "$TRACE_LEVEL"
then
    config_arg_val=debuglog4j.properties
else
    config_arg_val=tracelog4j.properties
fi
java -Dlog4j.debug=true "-Dlog4j.configuration=${config_arg_val}" -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addtax  example/ncbi_primates.txt ncbi  test.db -1 || exit
