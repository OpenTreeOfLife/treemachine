# emily jane mctavish map deepest synthesis test


High rank phylogeny P1: (((A1,B1),C1),D1);

Lower rank phylogeny P2: (((A1,B1),(B2,C1)),D1);

Taxonomy: (A1,(B1,B2)),C1,D1);

Naive expected synthesis: (((A1,B1),(B2,C1)),D1); because there is no conflict except with taxonomy

Actual expectation (I think) : ((A1,(B1,B2),),C1),D1); because B1 in tree P1 is mapped as (B1,B2)

CEH's note: The naive expectation assumes that the intention of the researcher is *not* to use B1 as an exemplar of the higher taxon B in phylogeny P1. I do not this is something we can generally assume, since systematists routinely intend precisely the opposite. Indeed, the fact that exemplar tips are so commonly used to represent deeper taxa is exactly why we started mapping deep in the first place. The problem arises from the fact that although this is a common practice, it may not always be correct to assume this either.

To clarify the original intentions of the researchers who chose the tips for the tree, we need disambiguating information, such the presence of other B taxa as in P2, or statements of exemplification intention for tips that could be recorded during source tree curation.
