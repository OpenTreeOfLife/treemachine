import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/induced_subtree",
            {u'node_ids': [u"ott3504", u"ott396446"]},
            check_blob([field(u'newick', check_string)]))

# test that includes ottids that aren't in tree
status += \
simple_test("/v3/tree_of_life/induced_subtree",
            {u"ott_ids":[292466, 501678, 267845, 666104, 316878, 102710, 176458]},
            check_blob([field(u'newick', check_string)]))

sys.exit(status)
