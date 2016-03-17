import sys
from check import *


basic_about_results = [field(u'num_source_studies', check_integer),
                       field(u'date_created', check_string),
                       field(u'num_source_trees', check_integer),
                       field(u'num_source_studies', check_integer),
                       field(u'taxonomy_version', check_string),
                       field(u'filtered_flags', check_list(check_string)),
                       field(u'root', check_node_blob),
                       field(u'synth_id', check_string)]

status = 0

status += \
simple_test("/v3/tree_of_life/about",
            {},
            check_blob(basic_about_results))

status += \
simple_test("/v3/tree_of_life/about",
            {u'include_source_list': True},
            check_blob(basic_about_results +
                       [field(u'source_list', check_list(check_string)),
                        field(u'source_id_map', check_source_id_map)]))

sys.exit(status)
