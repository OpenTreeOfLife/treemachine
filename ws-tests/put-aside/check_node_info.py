# Common code for the node_info tests

import sys, os
from opentreetesting import test_http_json_method, config

# Do a node_info test

def check_node_info(parameters):
    DOMAIN = config('host', 'apihost')
    SUBMIT_URI = DOMAIN + '/v3/tree_of_life/node_info'
    test, result, _ = test_http_json_method(SUBMIT_URI, "POST",
                                            data=parameters,
                                            expected_status=200,
                                            return_bool_data=True)
    if not test: sys.exit(1)
    node_id_key = u'node_id'
    check_node_result(result, node_id_key)
    # check_node_expectation(parameters, result, node_id_key, ...name...)
    valid_keys = [node_id_key, u'taxon', u'nearest_taxon', u'synth_id']
    check_result_keys(result, valid_keys)
    sys.exit(exit_status[0])

# Goal: shared this code between node_info and mrca tests

def check_node_result(result, node_id_key):
    node_id = check_result_type(result, node_id_key, unicode)
    synth_id = check_result_type(result, u'synth_id', unicode)

    if u'taxon' in result:
        taxon = check_result_type(result, u'taxon', dict)
        check_taxon(taxon, u'Alseuosmia banksii', [])
        if u'nearest_taxon' in result:
            sys.stderr.write('both taxon and nearest_taxon are present')
            lose()
        nearest_taxon = None
    elif u'nearest_taxon' in result:
        nearest_taxon = check_result_type(result, u'nearest_taxon', dict)
        check_taxon(nearest_taxon, None, [u'node_id'])
        taxon = None
    else:
        sys.stderr.write('neither taxon nor nearest_taxon is present\n')
        lose()

def check_node_expectation(parameters, result, node_id_key, want_name):
    want_node_id = parameters[u'node_id']

    # If request id is "ottNNN", then we expect a taxon in the result
    if (want_node_id[0:3] == u'ott'):
        if not u'taxon' in result:
            sys.stderr.write('no taxon; keys = {}\n'.format(result.keys()));
            lose()
        else:
            taxon = result[u'taxon']
            name = check_result_type(taxon, u'name', unicode)
            if want_name != None and name != want_name:
                sys.stderr.write('Expected taxon name {} but found {} instead\n'.format(want_name, name))
                lose()
    else:
        if u'taxon' in result:
            sys.stderr.write('surprised to find taxon; keys = {}\n'.format(result.keys()));
            lose()

# Check the fields of a taxon blob (value of 'taxon' or
# 'nearest_taxon' result).

def check_taxon(taxon, status, extras):
    tax_sources = check_result_type(taxon, u'tax_sources', list)
    if tax_sources != None:
        for source in tax_sources:
            if not isinstance(source, unicode):
                sys.stderr.write('taxonomy source is {} which is not a string\n'.format(source))
                lose()
    check_result_type(taxon, u'ott_id', int)
    # unique_name and rank are optional?
    
    if u'rank' in taxon:
        check_result_type(taxon, u'rank', unicode)

    if u'unique_name' in taxon:
        check_result_type(taxon, u'unique_name', unicode)

    check_result_keys(taxon, extras + ['ott_id', 'name', 'rank', 'tax_sources', 'unique_name'])

# Make sure nothing weird returned in result
def check_result_keys(result, valid_keys):
    for key in result.keys():
        if key not in valid_keys:
            sys.stderr.write('unexpected key {} (keys = {})\n'.format(key, result.keys()));
            lose()

# exit_status is hack to allow for multiple problems to be detected in
# a single test run.  It can get set to 1 (error) at any point, but then
# the testing can continue.
exit_status = [0]
def lose(): exit_status[0] = 1

# Check the presence and type of one of the nodes in the result blob.
# Returns None of not present, or the value if present.

def check_result_type(result, name, typo):
    value = result.get(name)
    if not isinstance(value, typo):
        if value == None:
            sys.stderr.write('{} missing; keys = {}\n'.format(name, result.keys()))
            lose()
        else:
            sys.stderr.write('{} is {} which is not a {}\n'.format(name, value, typo))
            lose()
        return None
    return value

