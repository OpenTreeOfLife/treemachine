# trivialconf synthesis test

Here tree1 is no real help:

    (A,B)

tree2 is consistent with this but identifies `E` as sister to `A`:

    ((A,E),B)

the taxonomy is a polytomy of groups `A`, `B`, and `E`

If the synthesis adds the branch leading to `A` as an edge of the root and fails to recognize that this is a trivial edge, then this edge will prevent the innocuous A+E group from being included. This would cause the input of tree2 to be ignored and the taxonomy to have the deciding voice.