
### Background
This theorem draft tries to formalize why MTH thought https://github.com/OpenTreeOfLife/treemachine/issues/156 and https://github.com/OpenTreeOfLife/treemachine/issues/78 had to be indicative of bugs.

Turns out he was incorrect, because the draftversion2 synthesis produced in Sept. 2014 was produced by an algorithm which uses a "relationship taxa"
conflict detection which violates assumption Synth3 below (see https://github.com/OpenTreeOfLife/treemachine/issues/157)
There also appears to be some additional clean up after the synthesis the add unattached nodes (search for "taxaleft" in that issue).
Thus the synthesis used does not obey the properties that MTH thought were properties of TAG synthesis described in the SBH paper.

##Notation:
  * `t` will be a input tree.
  * `L(t)` is the label set of the tree `t`
  * `L(t, v)` for a node `v` will be the label set of the subtree of `t` that descends from `v`
  * `L(V)` for a node V in the TAG will be the labels under this node in the TAG - the union of labels sets
      for all of the child nodes of `v` (each leaf node of the TAG is assigned single label)
  * `out(t, v)` is defined to be `L(t) - L(t, v)` . This the outgroup of node `v` with respect to the label set of `t`



Definitions:
## SBH-TAG

An SBH-TAG is a TAG constructed according to the following rules:

  * prop1. new input trees are added one at a time, and the only operation that adds nodes or edges to the TAG is the input of a new input tree. The input trees not have redundant nodes (nodes with that are not labelled and which have out-degree=1).

  * prop2. the operation of ingesting a input tree encodes edges of the input tree into edges in the TAG honoring the following properties.  Let the directed edge (child to parent) in the input tree be v -> v'.  There may be multiple edges in the TAG that correspond to this edge.  They will each have the properties described below.  So, we'll just focus on one edge in the TAG to describe the properties. We'll denote the edge V -> V'
    * prop2A. `L(V) ∩ L(t) = L(t, v)`. In words: "the label set of V when restricted to the label set of this tree is the label set of this input tree node"

    * prop2B. `L(V') ∩ out(t, v)` is not empty. In words "the parent in the TAG has at least one label from the outgroup of the input node v". In practice, as described by SBH, property 2B could be replace with the stronger property: `L(V') ∩ L(t, v') = L(t, v')` but we can use this weaker property for the proof below.

    * prop2C. node V' will also be the parent node of all of edges in the TAG that correspond to the "sister" edges of `v->v'`. This is guaranteed because the mapping of a parent node is based on finding or creating a set of nodes in the TAG that is the LICA of label set of the parent node in the input tree. The selection of the parent node in the TAG depends only on the properties of the parent node in the input tree (not on edge properties), so the same set of TAG nodes will be found for all of the sibling edges because all of these edges share the same parent node. 

  * prop3. Aligning additional trees may lead to the creation of more nodes, but will not alter `L(V)` for any node in the graph. The addition of a new tree may result in the need for "reprocessing" discussed at the top right of page 4. But MTH reads that as stating that the edges from a input tree will be deleted and the same rules will be executed again (on the graph which now has more nodes). Thus properties 2A, 2B, and 2C should still hold for all edges.


## SBH-TAG-synthetic tree

An SBH-TAG-synthetic tree is a directed tree extracted from an SBH-TAG by starting from a node (the root in practice, but that is not required for the proof), and the following properties:

  * Synth1. It is the induced tree obtained by using a subset of the edges in the TAG ("no new edges")

  * Synth2. At a each node it select a set of edges what cannot conflict with each other because the intersection of their label sets is empty.

  * Synth3. If the SBH-TAG-synthetic tree passes through a node `V` then the labels of `L(V)` can only occur as descendants of `V`

  * Synth4. there won't be redundant nodes in the synthesis. This is because:
    * Synth4A. the input trees will lack such nodes;
    * Synth4B. property prop2C guarantees that each node will have out-degree at least as large as the largest out-degree in any input tree that uses the node;
    * Synth4C. the conflict resolution is greedy in the sense that it selects a set of edges. And if there is an incoming edge that does not conflict with members of the selected set, then that edge is added to the set. (in other words, the synthesis only fails to traverse an edge if it conflicts with an edge that it has decided it will accept).


MTH was assumes property "Synth3" because he understood that as the reason for the conflict detection at a node. MTH assumed "Synth4C" because it is consistent with the goal of producing supertree that is full as possible. The relevant text in the paper does not explicitly state these 4 properties. Here it is: 

    "From this focal node, it proceeds breadth-first in
    determining which nodes to include in the synthesis as we traverse
    the TAG. At each node, the procedure examines the subtending
    nodes, and determines if any of them conflict. For synthesis,
    downstream conflict is determined by comparing the LICAs for
    each child. If the LICAs from nodes subtending the current node
    overlap, then these descendant subgraphs define incompatible
    subtrees, and are said to be in conflict (see the sections on
    measurements of support and conflict for an alternative method of
    detecting such conflict). In such cases, the procedure must make a
    decision about which path to prefer."



## input-tree-edge-bijection support

Let the "input-tree-edge-bijection support" (ITEB) be the 
number of input trees which support the edge in the following sense of support.
Input tree  `t` supports an edge `V->V'` in the synthetic tree if `t` 
contains some edge `v->v'` such that:
  * ited1: `L(V) ∩ L(t) = L(t, v)`
  * ited2: `L(V') ∩ out(t, v)` is not empty

If an edge has ITEB > 0, then collapsing the
edge in the synthetic tree could be considered to result in a worse tree in the sense
the collapsed tree
would be further from ITEB input trees (using the Robinson Foulds symmetric difference as distance).

### explanation of the name
Imagine that  we were restrict the supertree down to the leaf set of an input tree without deleting any redundant nodes
that result from the pruning.
We could then construct a mapping of edges in the supertree to edges in input tree where there is a correspondence if
the children of each of the edges are the ancestors of the same set taxa.

In this mapping (assuming no redundant nodes in the input tree, as stated above), and edge in the supertree
can correspond to 0 or 1 edge in the input tree.
However, each edge in the input tree can map to any number of edges in supertree, because there may be
redundant nodes in the supertree that resulted from pruning it down to the leaf set of this input tree.

The cases that count as support in teh ITEB sense are cases in which there is a bijection in the mapping of pruned supertree to an input tree.


## Theorem

   If a SBH-TAG-synthetic tree displays a label set that is the union of all of the input trees, then every edge in a SBH-TAG-synthetic tree has ITEB > 0.

### Proof
   Let `S(V')` be the set of labels in the synthetic tree that are descendants of TAG node `V'`

   Note that, by construction, if the `S(V') = L(V')` then each edge added has a ITEB contribution of 1 from the input tree that caused the edge to be added to the TAG. This is true because properties prop2A and prop2B of edge addition are precisely properties ited1 and ited2 of the ITEB criterion. If we are dealing with a "full" synthetic tree (no leaf labels lost), then the leaf sets relevant to the calculations of properties ited1 and ited2 are the same as those when the tree was added. Thus prop2A and prop2B imply ited1 and ited2.

   Synth1 guarantees that there are not other sources of edges that we have to worry about being in the synthetic tree.

### Note
Some of extra assumptions above are presented because I think that one could prove a stronger theorem that lacks the "If a SBH-TAG-synthetic tree displays a label set that is the union of all of the input trees" qualifier.
This is not immediately obvious, because we do have to worry about the case that a synthetic node was chosen but some members of the leaf set are dropped because of conflict.
This could result in prop2A and prop2B being true of the node in the TAG, but not of the node in the synthetic tree. Properties ited1 and ited2 could fail to be true of the node in the synthetic tree because some labels could have been lost due to conflict.
Thus, we have made the theorem conditional on the fact that the synthesis operation produces a "full" tree. 

I think that the form without the qualifier is provable, but have not worked on it much. The intuition is that, when we know an edge is traversed in the synthetic tree, then either:
  * one of its sibling edges from the same tree will also be in the input tree will in the synthetic tree, OR
  * a set of edges will be in the synthetic tree that conflicts with its sibling edges will be in the synthetic tree.
(this follows from the greediness Synth4C). Either case satisfies ited2.

We'd still need to show that we further synthesis in the subtree would not result in dropping the leaves that make ited1 hold. This seems like it could be true for the ranked synthesis methods. If it is the case that the construction of the TAG guarantees that:  the TAG can guarantee that if a node `V` is entered during synthesis, then synthesis of that node will produce a subtree that has all of the labels in `L(V)`. MTH had thought that this had been solved in the synthesis step used by open tree.
But it does not seem to be true of the algorithm that produced draftversion2.tre.

### some thoughts on modifications to guarantee full synthetic trees


I *think* that one could modify the TAG rules to guarantee that the synthesis would be "full".

The easiest course of action to implement, would be to simply add trivial edges from the node to the excluded leaf nodes.
This might result in polytomies that are bigger than they need to be (in the sense, there could be some phylogenetic statements about these tips
which are compatible with the synthetic tree, but are ignored by creating a polytomy including all of these leaves).

It might be possible to be smarter about it, if we added som more edges in TAG creation. In addition to the current edges, you'd need to add some lower priority edges that correspond to less resolved forms of the input input tree.
This would enable a fall back during tracing of the synthetic tree:
  * ModSynth1 conduct synthesis as described above (Synth1 - Synth4), then
  * before leaving a node `V'`, check for any labels in `L(V')`. If any aren't included in the edges that have been accepted, start checking the low priority edges in an order that pays attention to input tree rank and whether the number of descendants of the node. The lowest priority fall back would be the set of edges that adds the culled taxa as children of node `V'`

