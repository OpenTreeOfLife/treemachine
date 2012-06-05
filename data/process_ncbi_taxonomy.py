import sys,os,sqlite3

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print "python process_ncbi_taxonomy.py database outfile"
        sys.exit(0)
    
    con = sqlite3.connect(sys.argv[1])
    cur = con.cursor()
    sql = "select left_value,right_value from taxonomy where name_class = 'scientific name' and name = 'environmental samples';"
    cur.execute(sql)
    a = cur.fetchall()
    lefts = []
    rights = []
    for i in a:
        lefts.append(int(i[0]))
        rights.append(int(i[1]))

    sql = "select ncbi_id,parent_ncbi_id,name,left_value,right_value from taxonomy where name_class = 'scientific name' and (name not like '%environment%' and name not like '%uncultured%');"
    print sql
    cur.execute(sql)
    a = cur.fetchall()
    outfile = open(sys.argv[2],"w")
    count = 0
    for i in a:
        count += 1
        if count % 10000 == 0:
            print count
        outfile.write(str(i[0])+","+str(i[1])+","+str(i[2])+"\n")
    outfile.close()
