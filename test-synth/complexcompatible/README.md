# complexcompatible synthesis test

A complex example where the trees contain no conflict with one another, but do conflict with the taxonomy, and have partially overlapping sampling at many levels.

Tree1 is better resolved:

    (((((A1,A2),A3),A4),(A5,A6)),(((B1,B2),((B3,B4),B5)),(((C1,C2),(C3,C4)),(D1,D2))))

but contains fewer exemplar taxa than the compatible tree2:

    ((A1,P),(A6,(Q1,Q2)),(((B1,B3),(S1,S2)),((C1,D1),(T,U,V))));

The taxonomy conflicts with tree2 but not tree1.

Synthesis should recover a topology consistent with both trees, but should also include those taxa not sampled by either tree.

Interestingly, the expected tree in this case contains a soft polytomy--there is insufficient information to place the P clade exactly, although we can be sure it is more closely related to A1 than to A5 or A6, so it is placed in a polytomy at the base of the A1-A4 clade to indicate that it could go anywhere in that clade.