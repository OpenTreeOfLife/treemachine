import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/mrca",
            {u'node_ids': [u"ott3504", u"ott396446"]},
            check_blob([field(u'mrca', check_node_blob),
                        opt_field(u'nearest_taxon', check_taxon_blob)]))

sys.exit(status)
