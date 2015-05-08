#!/bin/bash
TEST_DIR=$(dirname $0)
HARNESS_DIR=$(dirname "$TEST_DIR")
TEST_NAME=$(basename "$TEST_DIR")
source "$HARNESS_DIR"/synth-test-harness.sh
testsynthesis "${TEST_NAME}" 1 2 3 4 6 7 8 10 11 12 13