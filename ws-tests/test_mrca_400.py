import sys
from check import *

status = 0

status += \
simple_test("/v3/tree_of_life/mrca",
            {u'ott_ids': [3504, 396446, 869834]},
            expected_status=400)

sys.exit(status)
