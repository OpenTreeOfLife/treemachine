#!/bin/sh

# For local testing you'll want to do something like
#  ./run_tests.sh host:apihost=http://localhost:7474 host:translate=true

# For testing devapi:
#  ./run_tests.sh host:apihost=https://devapi.opentreeoflife.org

if ! python -c 'import peyotl' 2>/dev/null;
then
    echo 'peyotl must be installed to run tests'
    exit 1
fi
num_t=0
num_p=0
failed=''
for fn in $(ls test_*.py)
do
    if test $fn = skipped_test_name_here.py ; 
    then
        echo test $fn skipped
    else
        if python "$fn" $* > ".out_${fn}.txt"
        then
            num_p=$(expr 1 + $num_p)
            /bin/echo -n "."
        else
            /bin/echo -n "F"
            failed="$failed \n $fn"
        fi
        num_t=$(expr 1 + $num_t)
    fi
done
echo
echo "By default, an 's' is written for every test skipped." 
echo "Passed or skipped $num_p out of $num_t tests."
if test $num_t -ne $num_p
then
    echo "Failures: $failed"
    exit 1
fi
