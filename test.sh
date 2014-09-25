# set the TREEMACHINE_SERVER environment variable in your shell to point the script at the right location, e.g.:
#
# TREEMACHINE_SERVER=devapi.opentreeoflife.org/taxomachine # to run remotely against devapi
#
# TREEMACHINE_SERVER=localhost:7476/db/data # to run locally on devapi

[ -z "$TREEMACHINE_SERVER" ] && TREEMACHINE_SERVER='localhost:7474/db/data' && export TREEMACHINE_SERVER
cd tests && nosetests -vs curl_tests.py