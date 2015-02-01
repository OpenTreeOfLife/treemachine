See [the README](README.md) for agenda.  Eventually put this in LaTeX.

The notation and terminology here is largely consistent with
[SBH](http://dx.doi.org/10.1371/journal.pcbi.1003223), with a few
exceptions, such as dropping subscripts and saying 'tip' instead of
'taxon'.

### DAGs

    Let
      G = a directed acyclic graph = <V, D>  
      V = vertices(G) = set of vertices of G
      D = edges(G) = child/parent relation

    Define a "tip" to be a terminal vertex (going away from the root).

    Assume that every tip has a label, and that within a graph, no two
    tips have the same label.

    Define
      labels(G) = labels of tips in vertices(G)

    Posit that the vertices of distinct graphs are disjoint except as
    noted.  This lets us write

      graph(v) = the unique graph containing vertex v

    Define
      sub(v) = subtension of v in graph(v) = the labels of all tips that
        are tipward of v in graph(v)
      out(v) = labels(graph(v)) - sub(v)    - v's "out-set"

    Let v, v' come from different graphs.  Define
      v ~ v'  if sub(v) ∩ out(v') = ∅ and sub(v') ∩ out(v) = ∅
        [compatibility / unification candidates]

### Source trees

    If a DAG is a tree, denote it by 'x'
    For trees we have #V = #D + 1
    The trees we're dealing with are called 'source trees'

    Let
      α = number of source trees
      β = number of vertices in a source tree (average? maximum? depends?)

### Tree alignment graphs (TAG)

    Let
      x ... be a set of source trees
      TAG = a DAG <Q, E>
      align = a function (v 'maps to' q or v 'aligns to' q) from
        vertices of x ... to Q
    such that
      labels(TAG) = union of labels(x) for all source trees x ...
      align(v) = q implies v ~ q
      there's an edge from q to q' (rootward) in E, iff there
        exists v' in vertices(some x), and v a child of v', such that
          align(v') = q' and align(v) = q 
      [??? double check this]

    Call such a DAG, equipped with such an align() function, a 'tree
    alignment graph'.

### SBH algorithm

    Given a set of source trees x ..., find a TAG for them.  (The TAG
    is not unique; this is just one particular strategy for obtaining
    one.)

    Start with a graph <Q, {}> and align() = {}.  They will grow
    monotonically as we proceed.  Q has one tip for each label
    occurring in x ...

    Traverse all source trees x ..., each one in preorder.
    For each internal vertex v:
      For each child v- of v:
        For each node q- such that align(v-) = q-:
          Search rootward from q- looking for q such that
          (a) v ~ q
          (b) v not~ any rootward neighbor of q
          [TBD: Explain how these are identified, and how the search space
           is pruned]
        (Note that v ~ q is decided using information coming from
          v's and q's children (tipward).)
      For some q having properties (a) and (b) [first one encountered?]:
        Set align(v) = q.
      If no such q is found:
        Add a fresh q to Q, and set  align(v) = q.

    This pretty clearly finds a TAG.

    TBD: Explain which sub/out sets are cached and why, vs. not

    Do this twice ???? or keep going until a fixed point is reached?

    TBD: What is the order of growth?

    [Hmm, some difficulty around the graph-disjointness
    proclamation... 'note' that the TAGs in successive iterations will
    share nodes]

### TBD: Synthesis

    Given a TAG <Q, E> with alignment function align() and an edge
    preference rule [total order on edges?], construct the tree <V, D>
    such that

      V = Q
      D ⊆ W
      the edge from q to parent(q) has the highest edge priority among
        all edges rootward from q
      ???

    "To make a tree from an entire TAG the procedure starts at the root
    node. From this focal node, it proceeds breadth-first in determining
    which nodes to include in the synthesis as we traverse the TAG."

### An aside on model theory

    To apply some model theory to this setup, take every vertex or
    node v to be a symbol in a logic, to be interpreted as some
    biological taxon ('taxon' in the sense of logical class of
    individual organisms or specimens).  The taxon that v is
    interpreted to be is denoted ⟦v⟧.

    (We could distinguish vertices/nodes from logical symbols, but
    that would be cumbersome.  Confusing the two is convenient and I
    believe harmless, since they're in 1-1 correspondence.  That is
    quite unlike confusing nodes and vertices with taxa, which leads
    to all sorts of problems!)

    An edge rootward from v to v' in some DAG is interpreted as saying
    that ⟦v⟧ is properly contained in ⟦v'⟧.

    If v and v' in a source tree share a parent p, we interpret that to
    mean that ⟦v⟧ and ⟦v'⟧ are disjoint.  We should not assume this
    holds in the TAG or in a synthetic tree, however.

    How do we interpret v ~ q and align(v) = q ?  I align(v) = q is
    supposed to mean that ⟦v⟧ = ⟦q⟧.  Our choice of interpretation of
    the vertices of a tree therefore depends on what the TAG turns out
    to be.  Maybe we could alternatively take it to mean that ⟦v⟧ ⊆
    ⟦q⟧ or something similar, taken together with some other
    constraint.  TBD: Figure this out.  Goes to fidelity of the
    algorithm to biology.

    sub(v) ∩ out(q) ≠ ∅ means there's something in ⟦v⟧ that's not in
    ⟦q⟧.

### An aside to review RCC-5

    The RCC-5 relation symbols are:

    notation     intended interpretation
     a = b        ⟦a⟧ and ⟦b⟧ have the same members (same extension)
     a < b        ⟦a⟧ is properly contained in ⟦b⟧        
     a > b        ⟦a⟧ properly contains ⟦b⟧
     a | b        ⟦a⟧ and ⟦b⟧ are disjoint
     a <> b       none of the above (they overlap without either
                  containing the other)

    In a model, exactly one of these relations holds between any pair
    of taxa.  RCC-5 is the logic whose syntax is these formulas, and
    only these, and whose rules of deduction are derivable given the
    intent (transitivity of <, >, and =, symmetry of =, |, and <>,
    heritability of |, a rather complex axiom for <>, perhaps
    others?).

    An alternative interpretation is to take ⟦a⟧ to be a set of tips.
    This is an idealization and will lead to equations that do not
    follow from the biology.

    A claim that one of the RCC-5 relationships holds between two
    taxa, when expressed in the RCC-5 logic, is called an
    'articulation'.

    * If sub(v) ∩ out(q) ≠ ∅, we cannot have v=q or v<q; one of v>q,
      v|q, or v<>q holds.
    * Dually for sub(q) ∩ out(v) ≠ ∅ - we have neither v=q nor v>q.
    * If both conditions hold, then the only possibilities are v|q and
      v<>q.
    * During TAG generation we don't even look at pairs that are
      known to be disjoint - they won't be linked except as 
      siblings, cousins, etc. in the TAG.  So if both conditions hold,
      we know we have an inconsistency v<>q.
    * If neither condition holds, then no possibility is ruled out.
      This is the v ~ q case and is where we have the option of 
      alignment.


TBD: Compare to [this google doc](https://docs.google.com/document/d/1Ow70obuqaAS3Ga35yrjm95aN9GhDOkedPQ8ikL2g8Hk)
