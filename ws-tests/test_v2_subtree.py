import sys
from check import *

subtree_result_fields = [field(u'newick', check_string),
                         field(u'supporting_studies', check_list(check_string)),
                         field(u'tree_id', check_string)] 

status = 0

status += \
simple_test("/v2/tree_of_life/subtree",
            {u'ott_id': 1084532}, # in Asterales
            check_blob(subtree_result_fields))

sys.exit(status)
