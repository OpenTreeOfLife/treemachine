import json, requests, sys

tests = [
    
    # graph of life
    ('graph/graphdb/about', {}),
    ('graph/graphdb/source_tree', {"tree_id":"pg_420_522_96e3dcc7d18b5ba7b96b888ef18fdf7c14c088fa"}),
    ('graph/graphdb/node_info', {"ott_id":292466}),
    ('graph/graphdb/node_info', {"node_id":3019509}),
    ('graph/graphdb/node_info', {"node_id":3019459}),
    ('graph/graphdb/node_info', {"ott_id":176458}),
    ('graph/graphdb/node_info', {"node_id":3019459, "ott_id":81461}),
    ('graph/graphdb/node_info', {"node_id":3019459888}),
    ('graph/graphdb/node_info', {"ott_id":292466}),
    ('graph/graphdb/node_info', {"node_id":3019509}),
    ('graph/graphdb/node_info', {"node_id":3019459}),
    ('graph/graphdb/node_info', {"ott_id":176458}),
    ('graph/graphdb/node_info', {"node_id":3019459, "ott_id":81461}),

    # draft tree of life
    ('tree_of_life/graphdb/about', {}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[292466, 501678, 267845]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[292466]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[412129, 536234]}),
    ('tree_of_life/graphdb/subtree', {"ott_id":3599390}),
    ('tree_of_life/graphdb/subtree', {"ott_id":176458}),
    ('tree_of_life/graphdb/subtree', {"ott_id":3599390, "node_id":3599390}),
    ('tree_of_life/graphdb/subtree', {"ott_id":81461}),
    ('tree_of_life/graphdb/subtree', {"ott_id":691846}),
    ('tree_of_life/graphdb/subtree', {"ott_id": 801601}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678, 267845, 666104, 316878, 102710]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678, 267845, 666104, 316878, 102710, 176458]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[3599390, 176458]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[412129, 536234]}),

]

url = "http://localhost:7474/db/data/ext/{}"

def run_test():

    for service, data in tests:
        yield exec_call, service, data

def exec_call(service, data):

    sys.stderr.write("\ncurl -X POST " + url.format(service) + " -H 'content-type:application/json' -d '" + json.dumps(data) + "\n")
    sys.stderr.flush()

    try:
        r = requests.post(url.format(service), json.dumps(data))
        d = json.loads(r.text)
        
        # need to check for stacktrace
        
        if 'error' in d:
            sys.stderr.write('error: ' + d['error'] + '\n')
            assert False
        elif 'exception' in d:
            sys.stderr.write('exception: ' + d['fullname'] + '\n' + d['stacktrace'] + '\n')
            assert False
        
        sys.stderr.flush()

    except Exception as ex:
        sys.stderr.write(ex)
        assert False
    

