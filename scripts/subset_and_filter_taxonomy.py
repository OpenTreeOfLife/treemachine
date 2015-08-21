"""
  Subset the OTT taxonomy.tsv file (http://files.opentreeoflife.org/ott/)
  Discards "bad" taxa i.e. those that treemachine itself prunes, so taxonomy will 
  reflect what is in treemachine (and, therefore, in the synthetic tree).

  Usage:

  python subset_taxonomy.py <ottID OR taxon_name> <taxonomy.tsv> <subsetted_taxonomy_name.tsv>
  
  To generate a newick of the taxonomy, do the following:

  java -jar treemachine_location/target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar converttaxonomy <subsetted_taxonomy_name.tsv> <subsetted_taxonomy_name.tre>
"""

import os
import sys
import getopt

## treemachine flags

# the following are for ott 2.8draft5 (used in the publication)
#tflags = ["major_rank_conflict", "major_rank_conflict_direct", "major_rank_conflict_inherited", 
#"environmental", "unclassified_inherited", "unclassified_direct", "viral", "nootu", "barren", 
#"not_otu", "incertae_sedis", "incertae_sedis_direct", "incertae_sedis_inherited", "extinct_inherited", 
#"extinct_direct", "hidden", "unclassified"]

# the following are for ott 2.9draft8
tflags = ["major_rank_conflict", "major_rank_conflict_inherited", "environmental",
"unclassified_inherited", "unclassified", "viral", "barren", "not_otu", "incertae_sedis",
"incertae_sedis_inherited", "extinct_inherited", "extinct", "hidden", "unplaced", "unplaced_inherited",
"was_container", "inconsistent", "inconsistent", "hybrid"]

def printhelp():
    print
    print 'Utility for subsetting the OpenTree Taxonomy (OTT)'
    print
    print 'Command options:'
    print '  -t STRING: target taxon, either name or (safer) OTTid. Required'
    print '  -i FILE: OTT taxonomy file (.tsv format). Required'
    print '  -o FILE: subsetted taxonomy filename. Required'
    print '  -p: prune terminal taxa. Optional'
    print '  -h: print this help'
    print

if __name__ == "__main__":
    
    oplist,args = getopt.getopt(sys.argv[1:],'t:i:o:ph')
    target = ''
    inf = ''
    outf = ''
    prune = False
    
    for e in oplist:
        if e[0] == '-h':
            printhelp()
            exit()
        elif e[0] == '-t':
            target = e[1]
        elif e[0] == '-i':
            inf = e[1]
        elif e[0] == '-o':
            outf = e[1]
        elif e[0] == '-p':
            prune = True
    
    if (target == '' or inf == '' or outf == ''):
        'Insufficient arguments given'
        printhelp()
        exit()
    
    print "target taxa: ",target
    infile = open(inf,"r")
    outfile = open(outf,"w")

    count = 0
    pid = {} #key is the child id and the value is the parent
    cid = {} #key is the parent and value is the list of children
    nid = {}
    nrank = {}
    sid = {}
    unid = {}
    flagsp = {}
    targetid = ""
    for i in infile:
        spls = i.strip().split("\t|")
        tid = spls[0].strip()
        parentid = spls[1].strip()
        name = spls[2].strip()
        rank = spls[3].strip()
        nrank[tid] = rank
        nid[tid] = name
        sid[tid] = spls[4].strip()
        unid[tid] = spls[5].strip()
        flags = spls[6].strip()
        badflag = False
        if len(flags) > 0:
            for j in tflags:
                if j in flags:
                    badflag = True
                    break
            if badflag == True:
                continue
        flagsp[tid] = flags
        pid[tid] = parentid
        if tid == target or name == target:
            print "name set: " + name + "; tid: " + tid
            targetid = tid
            pid[tid] = ""
        if parentid not in cid: 
            cid[parentid] = []
        cid[parentid].append(tid)
        count += 1
        if count % 100000 == 0:
            print count
    infile.close()
    
    stack = [targetid]
    while len(stack) > 0:
        tempid = stack.pop()
        outfile.write(tempid+"\t|\t"+pid[tempid]+"\t|\t"+nid[tempid]+"\t|\t"+nrank[tempid]+"\t|\t"+sid[tempid]+"\t|\t"+unid[tempid]+"\t|\t"+flagsp[tempid]+"\t|\t\n")
        if tempid in cid:
            for i in cid[tempid]:
                if prune == True:
                    if i in cid: # is the taxon a parent?
                        stack.append(i)
                else:
                    stack.append(i)
    outfile.close()

