import sys
from check import *

basic_node_info_results = [field(u'node_id', check_integer),
                           field(u'num_tips', check_integer),
                           field(u'num_synth_tips', check_integer),
                           field(u'in_synth_tree', check_boolean),
                           field(u'synth_sources', check_list(check_source_blob)),
                           field(u'tree_sources', check_list(check_source_blob)),
                           field(u'tree_id', check_string),
                           field(u'ott_id', check_integer),
                           field(u'name', check_string),
                           field(u'rank', check_string),
                           field(u'tax_source', check_string)]

status = 0

status += \
simple_test("/v2/graph/node_info",
            {u'ott_id': 396446},
            check_blob(basic_node_info_results))

def check_ottid_or_null(x, where):
    if x == u'null':
        return True
    else:
        return check_integer(x, where)

check_taxonlike_blob = check_blob([field(u'node_id', check_integer),
                                   field(u'ott_id', check_ottid_or_null),
                                   field(u'name', check_string),
                                   field(u'rank', check_string),
                                   field(u'unique_name', check_string)])

status += \
simple_test("/v2/graph/node_info",
            {u'ott_id': 396446,
             u'include_lineage': True},
            check_blob(basic_node_info_results +
                       [field(u'draft_tree_lineage', check_list(check_taxonlike_blob))]))

sys.exit(status)
