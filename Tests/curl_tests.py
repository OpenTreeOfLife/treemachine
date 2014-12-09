import json, os, requests, sys

tests = [
    
    # graph of life
    ('graph/graphdb/about', {}),
    ('graph/graphdb/node_info', {"ott_id":292466}),
    ('graph/graphdb/node_info', {"node_id":3019509}),
    ('graph/graphdb/node_info', {"node_id":3019459}),
    ('graph/graphdb/node_info', {"ott_id":176458}),
    ('graph/graphdb/node_info', {"ott_id":292466}),
    ('graph/graphdb/node_info', {"node_id":3019509}),
    ('graph/graphdb/node_info', {"ott_id":176458}),
    ('graph/graphdb/node_info', {"node_id":3019459}),

    # tests that produce errors intentionally. we need better test evaluation for these cases
#    ('graph/graphdb/node_info', {"node_id":3019459, "ott_id":81461}),
#    ('graph/graphdb/node_info', {"node_id":3019459888}),
#    ('graph/graphdb/node_info', {"node_id":3019459, "ott_id":81461}),
#    ('tree_of_life/graphdb/subtree', {"ott_id":3599390, "node_id":3599390}),
#    ('tree_of_life/graphdb/subtree', {"ott_id":176458}),
#    ('tree_of_life/graphdb/subtree', {"ott_id":691846}),
#    ('tree_of_life/graphdb/subtree', {"ott_id": 801601}),
#    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[3599390, 176458]}),

    # not sure if these failures are intentional or not
#    ('graph/graphdb/source_tree', {"tree_id":"pg_420_522_96e3dcc7d18b5ba7b96b888ef18fdf7c14c088fa"}),

    # draft tree of life
    ('tree_of_life/graphdb/about', {}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[292466, 501678, 267845]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[292466]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[412129, 536234]}),
    ('tree_of_life/graphdb/subtree', {"ott_id":3599390}),
    ('tree_of_life/graphdb/subtree', {"ott_id":81461}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678, 267845, 666104, 316878, 102710]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678, 267845, 666104, 316878, 102710, 176458]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[292466, 501678]}),
    ('tree_of_life/graphdb/mrca', {"ott_ids":[412129, 536234]}),
    
    # test cases related to https://github.com/OpenTreeOfLife/treemachine/issues/130
    # these are currently failing on all branches of treemachine, need to be fixed
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids":[797186,1069768,799880,325577,771681,265527,909424,916001,345202,515700,379288,821359,339361,459708,681762,429474,339369,833632,899964,279981,222794,1020638,388185,8861,374288,630561,2857781,1006246,756728,852873,40959,135756,236255,825707,989229,799391,1065959,259054,181153,339346,842200,991614,5256770,473836,532288,1006256,22343,272926,552986,679535,839027,605194,222781,11561,622458,737614,3904116,317784,3902985,621332,1004808,728680,481682,625750,881533,273185,1075736,1030433,1057986,504628,858818,133143,709884,164191,588060,309234,553005,789008,84004,828461,618115,711300,709900,930715,567481,776348,31926,1082415,494835,5334535,826664,210313,505234,885264,899976,657948,35933,555467,1004806,3904118,1058105,547635,964630,709887,135756,840257,303950,612448,222787,229330,831087,135756,1006251,5516225,1031796,152273,3902937,831084,135756,240937,596473,359073,543978,79347,709892,427869,681762,671802,243145,309263,19798,833644,833629,814207,1081728,183572,709876,426562,1060499,833642,947656,1000262,695389,612429,207474,1065946,709894,407502,413237,806245,5254103,378964,883797,964619,3942433,892369,638936,960334,883511]}),
    ('tree_of_life/graphdb/induced_subtree', {"ott_ids": [427869,621332,852873]}),

]

url = "http://{s}/ext/{r}"
server = os.environ['TREEMACHINE_SERVER']

def run_test():

    for service, data in tests:
        yield exec_call, service, data

def exec_call(service, data):

    service_url = url.format(s=server,r=service)
    sys.stderr.write("\ncurl -X POST " + service_url + " -H 'content-type:application/json' -d '" + json.dumps(data) + "'")
    sys.stderr.flush()

    r = requests.post(service_url, json.dumps(data))
    d = json.loads(r.text)
        
    # check for error returned by service itself
    if 'error' in d:
        sys.stderr.write('error: ' + d['error'] + '\n')
        assert False
    
    # check for java exception
    elif 'exception' in d:
        print d
        sys.stderr.write('exception: ' + d['fullname'] + '\n' + '\n'.join(d['stacktrace']) + '\n\n')
        assert False

    sys.stderr.flush()


