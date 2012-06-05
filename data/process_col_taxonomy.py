import sys,os
from collections import Counter

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print "python process_col_taxonomy.py taxa.txt outfile"
        sys.exit(0)
    
    infile = open(sys.argv[1],"r")
    outfile = open(sys.argv[2],"w")
    names = [] 
    parents = []
    count = 0
    for i in infile:
        spls = i.strip().split("\t")
        pnum = spls[5]
        parents.append(pnum)
        name = spls[9]
        names.append(name)
        count += 1
        if count % 100000 == 0:
            print count

    infile.close()
    print "counting"
    b  = Counter(names)
    dnames = []
    for i in b:
        if b[i] > 1:
            dnames.append(i)
    names = []
    b = Counter(parents)
    dparents = []
    dparents = b.keys()
    parents = []

    print "done counting"
    infile = open(sys.argv[1],"r")
    count = 0
    for i in infile:
        spls = i.strip().split("\t")
        num = spls[0]
        pnum = spls[5]
        name = spls[9]
        if name in dnames:
            if num in dparents:
                outfile.write(num+","+pnum+","+name+"\n")
        else:
            outfile.write(num+","+pnum+","+name+"\n")
        count += 1
        if count % 10000 == 0:
            print count
    outfile.close()
    infile.close()
