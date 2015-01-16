See README.md for agenda.  Eventually put this in LaTeX.


    alpha = number of source trees
    beta = number of vertices in a source tree (average? maximum? depends?)

    x = a source tree = <V, parent>
    V = vertices(x) = set of vertices of x
    D = edges(x) = child/parent relation
    #V = #D + 1

    TAG = tree alignment graph = <Q, E>   acyclic, directed.
    Q = set of nodes in TAG
    E = set of edges connecting nodes in Q
      [I think of these going tipward, like in the trees, but SBH
      says the edges go rootward... I'll try to be explicit about
      this saying 'rootward' or 'tipward' as needed]

    Define a "tip" to be a terminal vertex (furthest away from root).
    tips(x) = terminal vertices in vertices(x)

    Posit that if t ≠ t' then vertex sets, with the important exception of
    tips, are disjoint.  A tip of x is never the internal node of x'.
    This lets us write

      tree(v) = the (unique) tree containing v  (assuming v not a tip)

    Define:
    sub(v) = subtension of v in tree(v) (i.e. all tips that are tipward of
       v in tree(v)) or {v} if v is a tip
    sub(q) = subtension of q in TAG (all tips that are tipward of q in Q,
       or {q})
    out(v) = tips(tree(v)) - sub(v)    - v's "out-set"
    out(q) = tips(TAG) - sub(q)        - varies
    v ~ q  if sub(v) ∩ out(q) ≠ ∅ and sub(q) ∩ out(v) ≠ ∅ (compatible)
    v → q  v 'maps to' or 'aligns to' q (see below) (a subrelation of ~)
       v ~ v for all tips
    q ⇀ q' = there exists v such v → q' and v- → q for some child of v
             (same as E ???)

    Problem statement: compute [a? the?] TAG = <Q, E> and alignment
    relation → having the following properties

    1. The tips of Q are all the tips of source trees, and no more
    2. For any given set of tips s, there is at most one q in Q such that
        sub(q) = q    [uniqueness - is this necessary??]
    3. If v → q then v ~ q    [completeness]
    4. If v → q and q is reachable rootward from q- then 
         NOT(v → q-)     [minimality]

    ???? is this a necessary and sufficient set of conditions ????

-----------------------------------------------------------------------------

    'Algorithm' (iterative)

    Start with Q = E = → = {}.  These will grow monotonically [I think] as
    we proceed.

    Traverse all trees, each one in preorder.
    For each internal node v:
      For each child v- of v:
        For each node q- such that v- → q-:
          Search rootward from q- looking for q such that
          (a) v ~ q
          (b) v not~ any rootward neighbor q
          [TBD: Explain how these are identified, and how the search space
           is pruned]
      For q having properties (a) and (b):
        Add  v → q  to →, for each such q found or added.
      If no such q is found:
        Add  v → q  to →, for a new q.

    [This pretty clearly solves the stated problem, which makes me think
    either one or the other wrong.]

    TBD: Explain what is cached and why, vs. not

    Do this twice ???? or keep going until a fixed point is reached?

-----------------------------------------------------------------------------

    TBD: Synthesis.

    "To make a tree from an entire TAG the procedure starts at the root
    node. From this focal node, it proceeds breadth-first in determining
    which nodes to include in the synthesis as we traverse the TAG."

-----------------------------------------------------------------------------

    An aside on model theory:

    Every vertex or node v is taken to be a symbol in the logic, to be
    interpreted as a name for some biological taxon (or logical class),
    denoted by ⟦v⟧.

    (We could distinguish vertices/nodes from logical symbols, but that
    would be cumbersome.  Confusing the two is convenient and I believe
    harmless, since they're in 1-1 correspondence.  That is quite unlike
    confusing vertices and taxa, which leads to all sorts of problems!)

    An edge from rootward v to v' (in some tree x) is interpreted as saying
    that ⟦v⟧ is properly contained in ⟦v'⟧.  We interpret an edge from q
    rootward to q' to mean that ⟦q⟧ is properly contained in ⟦q'⟧.

    If v and v' share a parent p in some x, we interpret that to mean that
    ⟦v⟧ and ⟦v'⟧ are disjoint.  This does not hold in the TAG.

    How do we interpret v ~ q and v → q ?  I think it's supposed to mean that ⟦v⟧ =
    ⟦q⟧ - meaning that our interpretation of the vertices depends on what
    the TAG turns out to be.  Maybe we could also take it to mean that ⟦v⟧
    ⊆ ⟦q⟧, or ⟦v⟧ ⊇ ⟦q⟧, or something similar.  TBD: Figure this out.
    Goes to fidelity of the algorithm to biology.

    sub(v) ∩ out(q) ≠ ∅  means there's something in ⟦v⟧ that's not in ⟦q⟧.

    sub(q) ∩ out(v) ≠ ∅  means there's something in ⟦q⟧ that's not in ⟦v⟧.

-----------------------------------------------------------------------------

    An aside to review RCC-5:

    The RCC-5 relation symbols are:

    notation     intended interpretation
     a = b        ⟦a⟧ and ⟦b⟧ have the same members (same extension)
     a < b        ⟦a⟧ is properly contained in ⟦b⟧        
     a > b        ⟦a⟧ properly contains ⟦b⟧
     a | b        ⟦a⟧ and ⟦b⟧ are disjoint
     a <> b       none of the above (they overlap without either
                  containing the other)

    Exactly one of these relations holds between any pair of taxa.  RCC-5
    is the logic whose syntax is these formulas, and only these, and whose
    rules of deduction are easily derivable given the intent.
    (Transitivity of <, >, and =, symmetry of =, |, and <>, and a few
    axioms relating the relations to one another.)

    A claim, expressed in RCC-5, that one of the RCC-5 relationships holds
    between taxa, is called an 'articulation'.

    * If sub(v) ∩ out(q) ≠ ∅, we cannot have v=q or v<q; i.e. one of v>q,
      v|q, or v<>q holds.
    * Dually for sub(q) ∩ out(v) ≠ ∅ - neither v=q nor v>q.
    * If both conditions hold, then the only possibilities are v|q and
      v<>q.
    * If neither condition holds, then no possibility is ruled out.
    * During TAG generation we don't even look at pairs that are
      known to be disjoint.
