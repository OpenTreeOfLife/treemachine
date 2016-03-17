
import sys

from opentreetesting import test_http_json_method, config

from check_node_info import check_node_result, check_result_keys, exit_status

def check_mrca(parameters):
    DOMAIN = config('host', 'apihost')
    SUBMIT_URI = DOMAIN + '/v3/tree_of_life/mrca'
    test, result, _ = test_http_json_method(SUBMIT_URI, "POST",
                                            data=parameters,
                                            expected_status=200,
                                            return_bool_data=True)
    if not test: sys.exit(1)
    node_id_key = u'node_id'   #was 'mrca_node_id'
    check_node_result(result, node_id_key)
    # check_node_expectation(parameters, result, node_id_key)
    valid_keys = [node_id_key, u'taxon', u'nearest_taxon', u'synth_id',
                  u'node_ids_not_in_tree', u'ott_ids_not_in_tree']
    check_result_keys(result, valid_keys)
    sys.exit(exit_status[0])


check_mrca({u"node_ids":[u"ott501678", u"ott267845"], u"ott_ids":[292466,10101010]})

