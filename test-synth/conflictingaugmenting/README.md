# conflictingaugmenting synthesis test

This is designed as an extension of nontrivialaugmenting. Here tree2 has some agreement with tree1, but also conflict with it

Tree1 is once again much less complete:

    (((A,B),C),D)

tree2 is:

    (((((A,E),C),B),F),D)

The taxonomy is a polytomy of the groups.

I would expect synthesis to get:

    (((((A,E),B),C),F),D)

because:
  * `tree2` says `A`+`E` as a group (and no tree contradicts that)
  * `tree1` says `A` and `B` are sister relavtive to `C` and `D`. tree2 disagrees, but it is weak and ranked second.
  * tree2 says `A+E+B+C` vs `F` or `D`, and that is compatible with tree1
  * tree2 says `A+E+B+C+F` vs`D`, and that is compatible with tree1

Synthesis is returning:

    (((A,B),C),D,E,F);