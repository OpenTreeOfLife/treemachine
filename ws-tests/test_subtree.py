import sys
from check import *

status = 0

check_newick_result = check_blob([field(u'newick', check_string),
                                  field(u'supporting_studies', check_list(check_string))])

status += \
simple_test("/v3/tree_of_life/subtree",
            {u'node_id': u"ott3504"},
            check_newick_result)

# Test that default is no mrca labels
# 217260 = Cebus
simple_test("/v3/tree_of_life/subtree",
            {u'node_id': u'ott217260'},
            check_newick_result,
            is_right=lambda x: not (u'mrca' in x[u'newick']))

# Test ability to generate mrca labels
simple_test("/v3/tree_of_life/subtree",
            {u'node_id': u'ott217260', u'include_all_node_labels': True},
            check_newick_result,
            is_right=lambda x: u'mrca' in x[u'newick'])

status += \
simple_test("/v3/tree_of_life/subtree",
            {u'node_id': u'ott3504', u'format': u'arguson', u'height_limit': 3},
            check_blob([field(u'arguson', check_top_arguson_blob)]))

sys.exit(status)
