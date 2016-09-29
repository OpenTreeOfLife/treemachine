import sys
from check import *

induced_subtree_result_fields = [field(u'newick', check_string),
                                 field(u'tree_id', check_string),
                                 field(u'node_ids_not_in_tree', check_list(check_integer)),
                                 field(u'ott_ids_not_in_tree', check_list(check_integer)),
                                 field(u'node_ids_not_in_graph', check_list(check_integer)),
                                 field(u'ott_ids_not_in_graph', check_list(check_integer))]

status = 0

status += \
simple_test("/v2/tree_of_life/induced_subtree",
            {u'ott_ids': [1084532, 3826]}, # two families in Asterales
            check_blob(induced_subtree_result_fields))

sys.exit(status)
