#!/bin/bash
TEST_DIR="$(dirname $0)"
cd "$TEST_DIR" || exit
failures=0
conducted=0
failed=""
function runsynthtest {
    tag="$1"
    echo "running test tag: ${tag}"
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
        if test -z $failed
        then
            failed="$tag"
        else
            failed="${failed}, ${tag}"
        fi
    fi
    conducted=$(expr 1 + $conducted)
}

if test -z $FAILING_TREEMACHINE_TEST
then
    echo "FAILING_TREEMACHINE_TEST not in env. All tests will be run..."
    runsynthtest trivialconf
    runsynthtest usenodetaxa
    runsynthtest nontrivialaugmenting
else
    echo "Using FAILING_TREEMACHINE_TEST env to restrict to 1 test"
    runsynthtest $FAILING_TREEMACHINE_TEST
fi


cd - >/dev/null
echo "Failed ${failures} out of ${conducted} tests(s)."
if test $failures -gt 0
then
    echo "failing test tags: ${failed}"
    exit 1
fi

