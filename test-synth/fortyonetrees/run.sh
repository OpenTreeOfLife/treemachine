#!/bin/bash
TEST_DIR=$(dirname $0)
HARNESS_DIR=$(dirname "$TEST_DIR")
TEST_NAME=$(basename "$TEST_DIR")
source "$HARNESS_DIR"/synth-test-harness.sh
testsynthesis "${TEST_NAME}" 41 40 39 38 37 36 35 34 33 32 31 30 29 28 27 26 25 24 23 22 21 20 19 18 17 16 15 14 13 12 11 10 9 8 7 6 5 4 3 2 1