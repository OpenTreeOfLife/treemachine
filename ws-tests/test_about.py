import sys
from check import *

status = 0

status += \
simple_test("https://test.opentreeoflife.org/v3/tree_of_life/about",
            {},
            check_blob(basic_about_results))

status += \
simple_test("https://test.opentreeoflife.org/v3/tree_of_life/about",
            {u'include_source_list': True},
            check_blob(basic_about_results +
                       [field(u'source_list', check_list(check_string)),
                        field(u'source_id_map', check_source_id_map)]))

sys.exit(status)
