# nontrivialaugmenting synthesis test

This is designed as an extension of trivialconf, but the new taxa from tree 2 are not all added near the tips

Tree1 is once again much less complete:

    (((A,B),C),D);

but compatible with tree2:

    ~~(((A,E),B),F);~~

**EDIT** Stephen pointed out this readme has a typo for tree2. It should instead read:

    ((((A,E),B),F),C);

The taxonomy is a polytomy of the groups.

Just to get everything on one page, the expected tree (in the file 'expected.tre') is:

    (((((A,E),B),F),C),D);

The delivered result is:

    ((((A,E),B),C),D,F);

