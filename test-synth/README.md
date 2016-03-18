# testing synthesis

**WARNING** these tests are still a work in progress. Make sure that you agree that the tag/expected.tre is indeed the
tree that should be expected for the inputs before you spend a lot of time trying to figure out why the test is failing.


**Note** the scripts need `bash` not (`sh` or `dash`)

Each of the tests here creates an `<test-tag>/out` directory of the output of each treemachine step, and creates a tiny new db called `<test-tag>/test.db`

If you run an individual test, you have to remove these output **directories** before running the test.

**WARNING:** `run_synth_tests.sh` removes the temporary directories before it runs the test

# `run_synth_tests.sh` control script

    bash run_synth_tests.sh

This runs all of the tests

    FAILING_TREEMACHINE_TEST=conflictingaugmenting ./run_synth_tests.sh

will just run the tag specified (conflictingaugmenting in this case).

# `synth-test-harness.sh`

This just provides the bash function that means that creating a new test is as simple as creating a directory with called `<test-tag>`.
The directory structure should be:

    test-tag/taxonomy.tsv
    test-tag/synonyms.tsv
    test-tag/tree1.tre
    test-tag/tree2.tre
    ....
    test-tag/treeN.tre
    test-tag/expected.tre

Eventually, we'll use a tree parser to compare the output `<test-tag>-out/synth.tre` to `<test-tag>/expected.tre`