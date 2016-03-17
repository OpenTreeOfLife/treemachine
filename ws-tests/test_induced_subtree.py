import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/induced_subtree",
            {u'node_ids': [u"ott3504", u"ott396446"]},
            check_blob([field(u'newick', check_string)]))

sys.exit(status)

