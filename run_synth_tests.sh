#!/bin/bash
TEST_DIR="$(dirname $0)"
cd "$TEST_DIR" || exit
test-synth/run_synth_tests.sh || exit
cd - >/dev/null

