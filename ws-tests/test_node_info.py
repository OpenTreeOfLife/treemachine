import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/node_info",
            {u'node_id': u"mrcaott3504ott396446"},
            check_blob(node_blob_fields +
                       [field(u'source_id_map', check_source_id_map),
                        opt_field(u'lineage', check_list(check_node_blob))]))


status += \
simple_test("/v3/tree_of_life/node_info",
            {u'node_id': u"ott396446"},
            check_blob(node_blob_fields +
                       [field(u'source_id_map', check_source_id_map),
                        opt_field(u'lineage', check_list(check_node_blob))]))

status += \
simple_test("/v3/tree_of_life/node_info",
            {u'node_id': u"ott396446", u"include_lineage": True},
            check_blob(node_blob_fields +
                       [field(u'source_id_map', check_source_id_map),
                        field(u'lineage', check_list(check_node_blob))]))

sys.exit(status)
