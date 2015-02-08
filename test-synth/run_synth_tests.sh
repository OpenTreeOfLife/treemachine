#!/bin/bash
TEST_DIR="$(dirname $0)"
cd "$TEST_DIR" || exit
failures=0
conducted=0
failed=""
linkcreated=""
function runsynthtest {
    tag="$1"
    if test -z $tag
    then
        echo "An arg is required!"
        exit
    fi
    echo "running test tag: ${tag}"
    if test -d "${tag}"/out
    then
        rm "${tag}"/out/*
    fi
    if test -d "${tag}"/test.db
    then
        rm "${tag}"/test.db/index/lucene/node/graphNamedNodes/*
        rmdir "${tag}"/test.db/index/lucene/node/graphNamedNodes
        rm "${tag}"/test.db/index/lucene/node/graphTaxUIDNodes/*
        rmdir "${tag}"/test.db/index/lucene/node/graphTaxUIDNodes
        rm "${tag}"/test.db/index/lucene/node/sourceMetaNodes/*
        rmdir "${tag}"/test.db/index/lucene/node/sourceMetaNodes
        rm "${tag}"/test.db/index/lucene/node/sourceRootNodes/*
        rmdir "${tag}"/test.db/index/lucene/node/sourceRootNodes
        rm "${tag}"/test.db/index/lucene/node/synthMetaNodes/*
        rmdir "${tag}"/test.db/index/lucene/node/synthMetaNodes
        rmdir "${tag}"/test.db/index/lucene/node

        rm "${tag}"/test.db/index/lucene/relationship/sourceRels/*
        rmdir "${tag}"/test.db/index/lucene/relationship/sourceRels
        rm "${tag}"/test.db/index/lucene/relationship/synthRels/*
        rmdir "${tag}"/test.db/index/lucene/relationship/synthRels
        rmdir "${tag}"/test.db/index/lucene/relationship

        rm "${tag}"/test.db/index/lucene/*
        rmdir "${tag}"/test.db/index/lucene

        rm "${tag}"/test.db/index/*
        rmdir "${tag}"/test.db/index
        
        rm -f "${tag}"/test.db/* 
        rmdir "${tag}"/test.db || exit
        
    fi
    if ! bash ./"${tag}"/run.sh
    then
        failures=$(expr 1 + $failures)
        if test -z "$failed"
        then
            failed="$tag"
        else
            failed="${failed}, ${tag}"
        fi
        if test -L last-failed-test.db
        then
            rm last-failed-test.db
        fi
        if ln -s "${tag}"/test.db last-failed-test.db
        then
            linkcreated="1"
        fi
    fi
    conducted=$(expr 1 + $conducted)
}

if test -z $FAILING_TREEMACHINE_TEST
then
    echo "FAILING_TREEMACHINE_TEST not in env. All tests will be run..."
    for tagrun in */run.sh
    do
        tag=$(dirname ${tagrun})
        runsynthtest "${tag}"
    done
else
    echo "Using FAILING_TREEMACHINE_TEST env to restrict to 1 test"
    runsynthtest $FAILING_TREEMACHINE_TEST
fi


cd - >/dev/null
echo "Failed ${failures} out of ${conducted} tests(s)."
if test $failures -gt 0
then
    echo "failing test tags: ${failed}"
    if ! test -z $linkcreated
    then
        echo "The last-failed-test.db link has been reset to point to the last test which failed."
    else
        echo "The last-failed-test.db link does NOT reflect the last test to fail!"
    fi
    exit 1
fi

