#!/bin/bash

clear

# these tests have been passed
# re-run them as a change has been made to the code
FAILING_TREEMACHINE_TEST=conflictingaugmenting ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=mapdeepestmctavish ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=preferresolved ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=overlapthroughtaxon ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=manysmallrels ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=nontrivialaugmenting ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=nonmonophyletictaxon ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=missingchildren ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=dipsacales ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=maptohigher ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=usenodetaxa ./run_synth_tests.sh
