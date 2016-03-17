# Common type checking logic and types to be used by all tests

import sys, json
import traceback
from opentreetesting import get_obj_from_http
from opentreetesting import config

# Returns 1 for failure, 0 for success

def simple_test(path, input, check):
    DOMAIN = config('host', 'apihost')
    url = DOMAIN + path
    try:
        print 'checking', url
        output = get_obj_from_http(url, verb='POST', data=input)
        if isinstance(output, dict) and 'error' in output:
            # Should be 400, not 200
            print '** error', output
            return 1
        elif check(output, ''):
            return 0
        else:
            return 1
    except Exception, e:
        print '** exception', e
        traceback.print_exc()
        return 1

def check_integer(x, where):
    if isinstance(x, int) or isinstance(x, long):
        return True
    else:
        print '** expected integer but got', x, where
        return False

def check_string(x, where):
    if isinstance(x, unicode):
        return True
    else:
        print '** expected string but got', x, where
        return False

def check_source_id(x, where):
    if not isinstance(x, unicode):
        print '** expected string (source id) but got', x, where
        return False
    elif not (x == u'taxonomy') and not ('_' in x):
        print '** expected a source id but got', x, where
        return False
    else:
        return True

def field(name, check):
    return (name, check, True)

def opt_field(name, check):
    return (name, check, False)

def check_blob(fields):
    required = [name for (name, check, req) in fields if req]
    checks = {}
    for (name, check, req) in fields:
        checks[name] = check
    def do_check_blob(x, where):
        if not isinstance(x, dict):
            print '** expected dict but got', x, where
            return False
        win = True
        for name in x:
            if name in checks:
                check = checks[name]
                if not check(x[name], where + ' in ' + name):
                    win = False
            else:
                print "** unexpected field '%s' found among %s %s" % (name, x.keys(), where)
                win = False
        for name in required:
            if not (name in x):
                print "** missing required field '%s' not found among %s %s" % (name, x.keys(), where)
                win = False
        return win
    return do_check_blob

def check_list(check):
    def do_check_list(x, where):
        if not isinstance(x, list):
            print '** expected list but got', x, where
            return False
        for y in x:
            if not check(y, where):
                return False
        return True
    return do_check_list

# Check types of all keys and values in a dictionary

def check_dict(check_key, check_val):
    def do_check_dict(x, where):
        if not isinstance(x, dict):
            print '** expected dict but got', x, where
            return False
        ok = True
        for key in x:
            if not check_key(key, where):
                ok = False
            val = x[key]
            if not check_val(val, ' in ' + key + where):
                ok = False
        return ok
    return do_check_dict

taxon_blob_fields = [field(u'ott_id', check_integer),
                     field(u'name', check_string),
                     field(u'rank', check_string),
                     field(u'unique_name', check_string),
                     field(u'tax_sources', check_list(check_string))]

check_taxon_blob = check_blob(taxon_blob_fields)

check_single_support_blob = check_dict(check_source_id, check_string)

check_multi_support_blob = check_dict(check_source_id, check_list(check_string))

node_blob_fields = [field(u'node_id', check_string),
                    opt_field(u'taxon', check_taxon_blob),
                    field(u'num_tips', check_integer),
                    opt_field(u'supported_by', check_single_support_blob),
                    opt_field(u'resolves', check_single_support_blob),
                    opt_field(u'resolved_by', check_multi_support_blob),
                    opt_field(u'conflicts_with', check_multi_support_blob),
                    opt_field(u'partial_path_of', check_single_support_blob),
                    opt_field(u'terminal', check_single_support_blob)]

check_node_blob = check_blob(node_blob_fields)

check_source_tree_blob = check_blob([field(u'git_sha', check_string),
                                     field(u'tree_id', check_string),
                                     field(u'study_id', check_string)])

check_taxonomy_blob = check_blob([field(u'version', check_string),
                                  field(u'name', check_string)])

def check_source_blob(x, where):
    if isinstance(x, dict) and u'version' in x:
        return check_taxonomy_blob(x, where)
    else:
        return check_source_tree_blob(x, where)

check_source_id_map = check_dict(check_source_id, check_source_blob)

if False:
    pred = check_blob([field('value', check_integer)])
    print 'test:', pred(json.loads(sys.argv[1]), '')
