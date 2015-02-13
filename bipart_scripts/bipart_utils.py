import tree_reader
import sys


"""
this is used for the first bipartition comparison
0 means they are not compatible
1 means that they are child of where the outgroup is 
    within the ingroup of another
2 means that there is no overlap of the outgroups and they 
    can be combined. E.g.

        lf1     rt1
bipart1 A B C | D

        lf2   rt2
bipart1 D F   | P Q

i1 = lf1 ^ rt2 = A B C ^ P Q = 0

i2 = rt1 ^ lf2 = D F ^ D = D

Would return 1. means bipart2 could be child of bipart1:
A B C | D <-childof- D F | P Q ... ??? does this make sense?

"""
def test_childof(lf1,rt1,lf2,rt2):
    test = 0 #1, child of, 2 equivalent
    i1 = lf1.intersection(rt2)
    if len(i1) > 0:
        return 0
    i2 = rt1.intersection(lf2)
    if len(i2) > 0:#childof
        return 1
    elif len(i2) == 0:#equivalent
        return 2
    return test

"""
these will be the final nodes that need to be created
in the tag
the paths are the paths that will be traveled to create 
these nodes. the paths are created in the process_pairs
and the nodes are created from these paths in 
generate_nodes_from_pairs
"""
nodesetslf = []
nodesetsrt = []
nodesparents = {}#key is index, value is set of parents
paths = []


"""
this is the code that makes the paths. it is recursive
the start is the index of the vertex in the pairs to start
pairs  # dictionary, key is index of bipart, value is list of compatible child ofs
bpslf are the set of biparts left
bpsrt are the set of biparts right
curlf is the growing curlf
currt is the growing currt
relationship of curlf to lf is that curlf would be the child of lf
the recursion starts toward the tips and goes to the root and then back to the tips
lf,rt will be parent, curlf,currt will be child
if curlf intersects with rt (parent outgroup) BAD
if currt intersects with parent ingroup FINE

I imagine that you could send the tree biparts here and us that as the path constructor
"""
def process_pairs(start,pairs,bpslf,bpsrt,curlf,currt,path,level):

    lf = bpslf[start] # parent ingroup
    rt = bpsrt[start] # parent outgroup

    if start not in pairs:
#        print "\t"*level, "parent ", start, ": ", lf, " | ", rt, " has no possible nested children, stop."
        return

    print "\n", "\t"*level, "potential parent ", start, ": ",lf," | ",rt
    print "\t"*level, "potential child:", curlf," | ",currt
    
    o1 = curlf
    o2 = currt

    i1 = curlf.intersection(rt) # child in intersection with parent out
    i2 = curlf.intersection(lf) # child in intersection with parent in
    i3 = currt.intersection(rt) # child out intersection with parent out

    if len(i1) > 0:
        print "\t"*level, "child ingroup contains some parent outgroup, die"  
        return
    elif len(i2) == len(lf):
        print "\t"*level, "child ingroup contains *all* parent ingroup, die ... ? child does not resolve parent?"
        return
    else:
        print "\t"*level, "attempting to update child"
        curlf = lf.union(curlf)
        print "\t"*level, "1. add the parent ingroup to child ingroup. result: ", curlf
        s = "\t"*level + "checking if the (new) child ingroup and (old) child outgroup don't overlap..."
        if len(currt.intersection(curlf)) == 0:
            currt = rt.union(currt)
            print s, " they dont.\n", "\t"*level, "2. add the parent outgroup to the child outgroup. result: ", currt
        else:
            currt = rt 
            print s, "\t"*level, " they do.\n", "\t"*level, "2. replace the child outgroup with the parent outgroup. result: ", currt
#            print "die."
#            return
        print "\t"*level, "updated 'child': ", curlf, " | ", currt, " (new parent?)"
    
#    index = None

    path.append(start)
    print "\t"*level, "saving ", start, " on path. current path is: ", path
    print "\n", "\t"*level, "potential parents of ", start, " are ", pairs[start], ". moving on to them now."
    for i in pairs[start]:
        if i in path:
            continue
        #print bpslf[i],bpsrt[i]
        x = process_pairs(i,pairs,bpslf,bpsrt,curlf,currt,path,level+1) # recur on the potential parents (?) of the parent?
        if x != None:
            currt = currt.union(x[1])

    print "\n", "\t"*level, "done with parent ", lf, " | ", rt," and child ", o1, " | ", o2,"."
    print "\t"*level, "node to create: ",curlf,currt,path

    if len(curlf.intersection(currt)) > 0 :
        print "OVERLAPPING LEFT AND RIGHT!",curlt,currt
        sys.exit(0)
    if path not in paths:
        paths.append(path)
    return curlf,currt


"""
this takes the generated paths from process_pairs and does a similar thing
to process pairs but generates the nodes for the tag
"""
def generate_nodes_from_paths(start,pairs,bpslf,bpsrt,curlf,currt):
    #print "start",start,bpslf[start],bpsrt[start]
    lf = bpslf[start]
    rt = bpsrt[start]
    i1 = curlf.intersection(rt)
    i2 = curlf.intersection(lf)
    i3 = currt.intersection(rt)
    if len(i1) > 0:
        return
    elif len(i2) == len(lf):
        return
    else:
        #print rt,currt
        curlf = lf.union(curlf)
        if len(currt.intersection(curlf)) == 0:
            currt = rt.union(currt)
        else:
            currt = rt
    index = None
    #print curlf,currt
    if start in pairs:
        x = generate_nodes_from_paths(pairs[start],pairs,bpslf,bpsrt,curlf,currt)
        #print "x",x
        if x != None:
            currt = currt.union(x[1])
            #index = x[2]
    #print "\tnode to create: ",curlf,currt
    if len(curlf.intersection(currt)) > 0 :
        print "OVERLAPPING LEFT AND RIGHT!",curlt,currt
        sys.exit(0)
    match = False
    for i in range(len(nodesetslf)):
        if curlf == nodesetslf[i]:
            if currt == nodesetsrt[i]:
                match = True
                #nodesparents[i].add(index)
                #print curlf,currt,i,"->",index,start
                #index = i
                break
    if match == False:
        nodesetslf.append(curlf)
        nodesetsrt.append(currt)
        #nindex = len(nodesetslf)-1
        #if nindex not in nodesparents:
        #    nodesparents[nindex] = set()
        #nodesparents[nindex].add(index)
        #index = nindex
    return curlf,currt#,index

"""
this tests to make sure that there is a node for each node in the each tree
"""
def test_tree(tree,nodesetslf,nodesetsrt):#,nodeparents):
    root = set(tree.lvsnms())
    for i in tree.iternodes():
        if len(i.children) > 0 and i != tree: #need to add root node
            lvs = i.lvsnms()
            lft = set(lvs)
            rt = root - lft
            match = False
            for j in range(len(nodesetslf)):
                if lft.issubset(nodesetslf[j]) and rt.issubset(nodesetsrt[j]):
                    match = True
                    if "nodes" not in i.data:
                        i.data["nodes"] = set()
                    i.data["nodes"].add(j)
                    print i.get_newick_repr(False),j,nodesetslf[j],nodesetsrt[j]
            if match == False:
                print "NO MATCH for tree node: "+i.get_newick_repr(False)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print "python "+sys.argv[0]+" infile.tre"
        sys.exit(0)
    trees = []
    infile = open(sys.argv[1],"r")
    for i in infile:
        trees.append(tree_reader.read_tree_string(i))
    infile.close()
    bipartslf = []
    bipartsrt = []
    #this generates the bipartions from the tree
    for i in trees:
        root = set(i.lvsnms())
        for j in i.iternodes():
            if len(j.children) > 0:# and i != j:
                lvs = j.lvsnms()
                lft = set(lvs)
                rt = root - lft
                bipartslf.append(lft)
                bipartsrt.append(rt)
    #these are the bipartitoins that are added as a result of summing like ab|e and ac|e -> abc|e
    addlf = []#added as a result of equivalent
    addrt = []#added as a result of equivalent
    #add just overlapping
    for r in range(len(bipartslf)):
        i = bipartslf[r]
        j = bipartsrt[r]
        #print i,j
        for s in range(len(bipartslf)):
            k = bipartslf[s]
            l = bipartsrt[s]
            if r != s:
                #print "\t",k,l
                tc = test_childof(i,j,k,l)
                #print "\t\t",tc
                if tc > 0: #these are compatible, 2 is they are to be summed
                    #if r not in bipart_pairs:
                    #    bipart_pairs[r] = set()
                    #bipart_pairs[r].add(s)
                    if tc == 2:
                        iu = i.union(k)
                        ij = j.union(l)
                        match = False
                        for t in range(len(addlf)):
                            if iu == addlf[t] and ij == addrt[t]:
                                match = True
                                break
                        for t in range(len(bipartslf)):
                            if iu == bipartslf[t] and ij == bipartsrt[t]:
                                match = True
                                break
                        if match == False:
                            addlf.append(iu)
                            addrt.append(ij)
    #adding the summed biparts to the total set
    for i in addlf:
        bipartslf.append(i)
    for i in addrt:
        bipartsrt.append(i)
        
    #now do the pairwise check for potential child of 
    #this is the graph of potential child of bipartitions
    bipart_pairs = {} #key is index, value is list of indices
    for r in range(len(bipartslf)):
        i = bipartslf[r]
        j = bipartsrt[r]
        print i,j
        for s in range(len(bipartslf)):
            k = bipartslf[s]
            l = bipartsrt[s]
            if r != s:
                print "\t",k,l
                tc = test_childof(i,j,k,l)
                print "\t\t",tc
                if tc == 1: # 1 is child of, 2 is to be summed
                    if r not in bipart_pairs:
                        bipart_pairs[r] = set()
                    bipart_pairs[r].add(s)

    #just printing out the graph
    print bipart_pairs

    #start at each vertex and postorder create all the possible paths through the graph
    for i in bipart_pairs:
        print "\ngenerating nested compatibility for: ",i,bipartslf[i],bipartsrt[i]
        process_pairs(i,bipart_pairs,bipartslf,bipartsrt,set(),set(),[],0)
        print "\ngenerated all paths for ", i

    #these are the paths from which the tag nodes will be created
    print "paths",paths
    for i in range(len(paths)):
        # for each path list, make a parent child dictionary
        #so from 0,1,2,3 to 0:1,1:2,2:3...
        temp_pairs = {}
        for j in range(len(paths[i])):
            if j+1 < len(paths[i]):
                temp_pairs[paths[i][j]] = paths[i][j+1]
        generate_nodes_from_paths(paths[i][0],temp_pairs,bipartslf,bipartsrt,set(),set())
    for i in range(len(nodesetslf)):
        print i,nodesetslf[i],nodesetsrt[i]#,nodesparents[i]

    #make the parent connections
    #this needs to be verified
    nodeparents = {}
    for i in range(len(nodesetslf)):
        parentlf = nodesetslf[i]
        parentrt = nodesetsrt[i]
        for j in range(len(nodesetsrt)):
            if j > i:
                chlf = nodesetslf[j]
                chrt = nodesetsrt[j]
                i1 = chlf.intersection(parentrt)
                if len(i1) > 0:
                    continue
                i2 = chlf.intersection(parentlf)
                if len(i2) == len(chlf):
                    print "child :",j,"parent :",i

    #test and make sure that all the trees pass the test
    for i in trees:
        test_tree(i,nodesetslf,nodesetsrt)#,nodesparents)
