#!/bin/bash
# force removal of existing results
if [ "$1" == "-f" ]
    then
        echo -e "\nRemoving previous results:\n\trm -rf nontrivialaugmenting-*\n"
        rm -rf nontrivialaugmenting-*
fi
source synth-test-harness.sh
testsynthesis nontrivialaugmenting 1 2
