#include <iostream>
#include <fstream>
#include <string>
#include <cstring>
#include <unordered_set>
#include <unordered_map>
#include <stack>
#include <list>
class Node {
    public:
    int taxon_index;
    int par_index;
    std::string name;
    std::list<Node *> children;
};
typedef std::unordered_map<int, Node*> id_to_node;
typedef std::unordered_set<int> indices_to_print;
typedef std::unordered_set<std::string> str_collection;
const unsigned long num_hash_buckets = 5500000;

/// Searches s (starting at position pos for the substring delimited by `delim' and ending with ind-th
//      occurrence of delim. Returns false if the search fails to find `ind` copies of delim
//      if it returns true, then output will equal the substring, and pos will be set to
//      the last occurrence of delim;
// This is like 0-based column searching for columns separated by delim
bool find_ith(const std::string & s, const char delim, unsigned ind, std::string & output, std::size_t &pos);

inline bool find_ith(const std::string & s, const char delim, unsigned ind, std::string & output, std::size_t &pos) {
    std::size_t prev_pos = pos;
    for (unsigned i = 0; i <= ind; ++i) {
        prev_pos = pos;
        ++pos;
        pos = s.find(delim, pos);
        if (pos == std::string::npos) {
            return false;
        }
    }
    if (ind > 0) {
        ++prev_pos;
    }
    output = s.substr(prev_pos, pos - prev_pos);
    return true;
}

Node * get_new_node() {
/*    static unsigned int blockSize = 10000;
    static int index = blockSize;
    static std::list<std::vector> allocated_blocks; */
    return new Node();
}

// massive memory leak caused by not deleting any nodes
inline Node * read_names(std::istream & inf,
                       id_to_node & id2node,
                       str_collection &all_parents,
                       str_collection &dup_names,
                       const std::string & taxon_to_find,
                       bool in_triples) {
#   if defined(USING_HASH_SET)
        str_collection all_names(num_hash_buckets);
#   else
        str_collection all_names;
#   endif
    std::string line;
    if (!in_triples) {
        std::getline(inf, line);
    }
    unsigned line_num = 1;
    Node * clade_root = nullptr;
    for(;;) {
        ++line_num;
        line.clear();
        std::getline(inf, line);
        std::size_t pos = 0;
        std::string tax_id;
        std::string parent;
        std::string name;
        if (in_triples) {
            if (!find_ith(line, ',', 0, tax_id, pos)) { // get the 1st column (index 0)
                if (inf.good()) {
                    std::cerr << "Expecting at least a word before a comma, but did not find them at line " << line_num << '\n';
                    exit(1);
                }
                return clade_root; // EOF!
            }
            pos++;
            std::size_t prev_pos = pos;
            if (!find_ith(line, ',', 0, parent, pos)) { // get the 6th column (index skip + 0 + skip + 4)
                if (line[prev_pos] != ',') {
                    if (inf.good()) {
                        std::cerr << "Expecting at least 1 commas, but did not find them at line " << line_num << '\n';
                        exit(1);
                    }
                    return clade_root; // EOF!
                }
                pos = prev_pos;
            }
            pos++;
            name = line.substr(pos, line.length() - pos);
        }
        else {
            //std::cerr << "line = \"" << line << "\"\n";
            if (!find_ith(line, '\t', 0, tax_id, pos)) { // get the 1st column (index 0)
                if (inf.good()) {
                    std::cerr << "Expecting at least 1 tab, but did not find them at line " << line_num << '\n';
                    exit(1);
                }
                return clade_root; // EOF!
            }
            if (!find_ith(line, '\t', 4, parent, pos)) { // get the 6th column (index skip + 0 + skip + 4)
                if (inf.good()) {
                    std::cerr << "Expecting at least 6 tabs, but did not find them at line " << line_num << '\n';
                    exit(1);
                }
                return clade_root; // EOF!
            }
            ++pos;
            if (!find_ith(line, '\t', 3, name, pos)) { // get the 10th column (index 9, but the skip ahead of pos => init skip + 5 + next skip + 3 = 10
                std::cerr << "Expecting at least 10 tabs, but did not find them at line " << line_num << '\n';
                exit(1);
            }
        }
        //std::cerr << "Line " << line_num << ":  parent=\"" << parent << "\" name=\"" << name << "\"\n";
        if (line_num % 100000 == 0) {
            std::cerr << line_num << '\n';
        }
        Node * nd = get_new_node();
        nd->name = name;
        int nd_num = std::atoi(tax_id.c_str());
        nd->taxon_index = nd_num;
        nd->par_index = std::atoi(parent.c_str());
        id2node[nd_num] = nd;

        if (all_names.find(name) != all_names.end() ) {
            dup_names.insert(name);
        }
        if (name == taxon_to_find) {
            clade_root = nd;
        }
        all_names.insert(name);
        all_parents.insert(parent);
    }
    return clade_root;
}


int main(int argc, char * argv[]) {
    int flag_index = -1;
    bool in_triples = false;
    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "-p") == 0) {
            flag_index = i;
            in_triples = true;
        }
    }
    if ((in_triples && argc != 5) || ((!in_triples) && argc !=4)) {
        std::cerr << "Expecting\n  " << argv[0] << " taxa.txt taxon outfile\nWhere \"taxon\" is the name of a taxon whose descendants will be retained.\nThe -p flag can be used to handle a file that has already been processed into triples.\n";
        return 1;
    }
    int curr_ind = 1;
    if (curr_ind == flag_index) {
        ++curr_ind;
    }
    std::ifstream infile(argv[curr_ind++]);
    if (!infile.good()) {
        std::cerr << "Could not open\n  " << argv[1] << "\n";
        return 1;
    }
    if (curr_ind == flag_index) {
        ++curr_ind;
    }
    std::string taxon(argv[curr_ind++]);
    if (curr_ind == flag_index) {
        ++curr_ind;
    }
    std::ofstream outfile(argv[curr_ind++]);
    if (!outfile.good()) {
        std::cerr << "Could not open\n  " << argv[2] << "\n";
        return 1;
    }
#   if defined(USING_HASH_SET)
        str_collection all_parents(num_hash_buckets);
        str_collection dup_names(300000);
#   else
        str_collection all_parents;
        str_collection dup_names;
#   endif
    id_to_node id2node;
    Node * root = read_names(infile, id2node, all_parents, dup_names, taxon, in_triples);
    if (root == nullptr) {
        std::cerr << "Taxon \"" << taxon << "\" not found!\n";
        return 1;
    }

    for (id_to_node::const_iterator mIt = id2node.begin(); mIt != id2node.end(); ++mIt) {
        Node * nd = mIt->second;
        if (id2node.find(nd->par_index) == id2node.end()) {
            if (nd->par_index) {
                std::cerr << "Parent " << nd->par_index << " not registered in map\n";
            }
        }
        else {
            Node * par = id2node[nd->par_index];
            par->children.push_back(nd);
        }
    }
    if (root->children.empty()) {
        std::cerr << "Taxon \"" << taxon << "\" has no children. That is not much of a tree!\n";
        return 1;
    }
    std::cerr << "back from read_names. " << dup_names.size() << " non unique names\n";
    std::stack<Node *> nd_stack;
    outfile << "1,0,root\n";
    outfile << root->taxon_index << "," << 1 << "," << root->name << "\n"; // zero out the ancestor for our pseudo-root node
    std::list<Node *>::reverse_iterator rcIt = root->children.rbegin();
    for (; rcIt != root->children.rend(); ++rcIt) {
        nd_stack.push(*rcIt);
    }
    Node * curr_nd = nd_stack.top();
    nd_stack.pop();

    unsigned num_written=0;
    for (;;) {
        ++num_written;
        outfile << curr_nd->taxon_index << "," << curr_nd->par_index << "," << curr_nd->name << "\n";
        if (curr_nd->children.empty()) {
            if (nd_stack.empty()) {
                break;
            }
        }
        else {
            std::list<Node *>::reverse_iterator cIt = curr_nd->children.rbegin();
            for (; cIt != curr_nd->children.rend(); ++cIt) {
                nd_stack.push(*cIt);
            }
        }
        curr_nd = nd_stack.top();
        nd_stack.pop();
    }

    std::cerr << num_written << " taxonomic names written.\n";


//    read_write_unique_and_ancestral_name(outfile, infile2, dup_names, all_parents);
/*
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
*/
    return 0;
}
