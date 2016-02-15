#!/usr/bin/env python
import sys, os
from opentreetesting import test_http_json_method, config
DOMAIN = config('host', 'apihost')
SUBMIT_URI = DOMAIN + '/v3/tree_of_life/node_info'
TEST_NAME = u'Alseuosmia banksii'
test, result, _ = test_http_json_method(SUBMIT_URI, "POST",
                                        data={"ot_node_id": "ott901642"},
                                        expected_status=200,
                                        return_bool_data=True)
if not test:
    sys.exit(1)
if u'ott_id' not in result:
    sys.stderr.write('ott_id not returned in result {}\n'.format(result))
    sys.exit(1)
taxonName = result[u'name']
if taxonName != TEST_NAME:
    errstr = 'Expected taxon name {} but not found in \n{}\n'
    sys.stderr.write(errstr.format(TEST_NAME,result))
    sys.exit(1)
