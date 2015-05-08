# preferresolved synthesis test

This is designed to check whether more resolved paths are chosen in cases where the descendant tip sets are the same (i.e. trivial conflict), even when there are multiple intervening nodes between the tips and the node where the trivial conflict is identified. 

Tree1 is much less resolved:

    (((A1,A2,A3,A4),(B1,B2,B3,B4)),C)

but has the same taxa and is compatible with tree2:

    (((((A1,A2),A3),A4),(((B1,B2),B3),B4)),C);

The taxonomy is a polytomy of the everything.

Synthesis should recover the tree2 topology even if tree 1 is ranked more highly, because the trees are compatible but tree2 is better resolved.