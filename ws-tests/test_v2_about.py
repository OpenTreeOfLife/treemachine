import sys
from check import *

basic_about_results = [field(u'date', check_string),
                       field(u'num_tips', check_integer),
                       field(u'num_source_studies', check_integer),
                       field(u'taxonomy_version', check_string),
                       field(u'root_node_id', check_integer),
                       field(u'root_ott_id', check_integer),
                       field(u'root_taxon_name', check_string),
                       field(u'tree_id', check_string)]

status = 0

status += \
simple_test("/v2/tree_of_life/about",
            {u'study_list': False},
            check_blob(basic_about_results))

status += \
simple_test("/v2/tree_of_life/about",
            {},                 # default is True
            check_blob(basic_about_results +
                       [field(u'study_list', check_list(check_source_blob))]))

sys.exit(status)
