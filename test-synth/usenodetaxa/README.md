# usenodetaxa synthesis test

Here tree1 provides a sparse backbone:

    (((A,B),C),D);

tree2 is consistent with this but identifies `E1` as sister to `A`

the taxonomy is a polytomy of groups `A` - `E` (with `E` containing 2 tips `E1` and `E2`)

If the synthesis algorithm uses "relationship taxa" to detect conflict, then it will allow the taxonomy to add `E` at the wrong place.