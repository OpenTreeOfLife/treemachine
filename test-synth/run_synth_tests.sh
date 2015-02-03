#!/bin/bash
TEST_DIR="$(dirname $0)"
cd "$TEST_DIR" || exit
failures=0

function runsynthtest {
    tag="$1"
    echo "${tag}"
    if test -d "${tag}"-out
    then
        rm -r "${tag}"-out
    fi
    if test -d "${tag}"-test.db
    then
        rm -r "${tag}"-test.db
    fi
    if ! bash ./run-"${tag}".sh
    then
        failures=$(expr 1 + $failures)
    fi
}

# detect that trivial edges don't conflict...
runsynthtest trivialconf


cd - >/dev/null
echo $failures "failures"
if test $failures -gt 0
then
    exit 1
fi

