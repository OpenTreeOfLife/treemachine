import sys
from check import *

def check_ottid_or_null(x, where):
    if x == u'null':
        return True
    elif check_integer(x, where):
        return True
    else:
        print '** expected OTT id or "null", but got', x, where
        return False

mrca_result_fields = [field(u'mrca_node_id', check_integer),
                      field(u'invalid_node_ids', check_list(check_integer)),
                      field(u'invalid_ott_ids', check_list(check_integer)),
                      field(u'node_ids_not_in_tree', check_list(check_integer)),
                      field(u'ott_ids_not_in_tree', check_list(check_integer)),
                      field(u'tree_id', check_string),
                      field(u'ott_id', check_ottid_or_null),
                      field(u'mrca_name', check_string),
                      field(u'mrca_rank', check_string),
                      field(u'mrca_unique_name', check_string), 
                      field(u'nearest_taxon_mrca_ott_id', check_integer),
                      field(u'nearest_taxon_mrca_name', check_string),
                      field(u'nearest_taxon_mrca_rank', check_string),
                      field(u'nearest_taxon_mrca_unique_name', check_string),
                      field(u'nearest_taxon_mrca_node_id', check_integer)] 

def is_right(result):
    return 3 in result[u'ott_ids_not_in_tree']

status = 0

status += \
simple_test("/v2/tree_of_life/mrca",
            {u'ott_ids': [1084532, 3826, 2, 3, 5]}, # two families in Asterales, and some bogus
            check_blob(mrca_result_fields),
            is_right=is_right)

sys.exit(status)
