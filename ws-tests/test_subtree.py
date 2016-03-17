import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/subtree",
            {u'node_id': u"ott3504"},
            check_blob([field(u'newick', check_string)]))

sys.exit(status)
