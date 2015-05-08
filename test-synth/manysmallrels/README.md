# manysmallrels synthesis test

A challenge to the combinatorics of the relationship selection procedure.

There are 13 non-overlapping trees each containing two branches at the root, each subtending three tips.

(((A1,B1),C1),((D1,E1),F1))
(((G1,H1),I1),((J1,K1),L1))
(((M1,N1),O1),((P1,Q1),R1))
(((S1,T1),U1),((V1,W1),X1))

(((E2,F2),G2),((H2,I2),J2))
(((K2,L2),M2),((N2,O2),P2))
(((Q2,R2),S2),((T2,U2),V2))

(((C3,D3),E3),((F3,G3),H3))
(((I3,J3),K3),((L3,M3),N3))
(((O3,P3),Q3),((R3,S3),T3))
(((U3,V3),W3),((X3,Y3),Z3))

The taxonomy contains taxa with 26 tips each. Each tree maps only to one of these taxa. There will be many numerous incoming relationships at the nodes corresponding to each of the three taxa.

Synthesis should recover a combined topology that contains the three taxa from the taxonomy with the input tree topologies nested within them. This test does not address topological expectations as much as it does the runtime of synthesis, but currently we use an approximate method to identify the best sets for large numbers of relationships. Since there is no conflict in this case, the approximate method should still find the best solution.