package org.opentree.tag.treeimport;

import jade.tree.JadeNode;
import jade.tree.NodeOrder;
import jade.tree.Tree;
import jade.tree.TreeNode;
import jade.tree.TreeParseException;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class ProcessSubproblems {

    public String subproblemdir = "/home/smitty/Downloads/export-sub-temp";
    public String subproblemdirTN = "/home/smitty/Downloads/export-sub-temp";
    public String mappedShallowDir = "/home/smitty/Downloads/mappedShallowTrees";
    public String taxonomyfile = "/home/smitty/TEMP/ott/taxonomy.tsv";
    public String outdir = "/home/smitty/Downloads/processed_export/";

    //key will be the ottId and the value will be the source names
    HashMap<String, ArrayList<String>> treenames = new HashMap<String, ArrayList<String>>();
	//key is source tree name and the value is the set of all the ids that can be mapped in the source tree
    // ones not found in here will be pruned from the trees
    HashMap<String, HashSet<String>> mappedShallowIds = new HashMap<String, HashSet<String>>();

    //taxonomy things
    HashMap<String, String> tax_parents = new HashMap<String, String>();

    public ProcessSubproblems() {
        /*
         * read the tree file names
         */
        File dir = new File(subproblemdirTN);
        for (File fl : dir.listFiles()) {
            if (fl.getName().contains("-tree-names.txt") == false) {
                continue;
            }
            String fn = fl.getName().split("-")[0] + ".tre";
            treenames.put(fn, new ArrayList<String>());
            try {
                BufferedReader br = new BufferedReader(new FileReader(fl));
                String st = "";
                while ((st = br.readLine()) != null) {
                    //if(st.trim().equals("TAXONOMY")==false)
                    treenames.get(fn).add(st.trim());
                }
                if (treenames.get(fn).size() == 0) {
                    treenames.remove(fn);
                }
                br.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        System.out.println("files with trees:" + treenames.size());

        dir = new File(subproblemdir);
        for (File fl : dir.listFiles()) {
            String fn = fl.getName();
            if (fn.contains(".tre") == false) {
                continue;
            }
            if (treenames.containsKey(fn) == false) {
                continue;
            }
            try {
                System.out.println(fn);
                FileReader fr = new FileReader(fl);
                BufferedReader br = new BufferedReader(fr);
                boolean opened = false;
                boolean first = true;
                FileWriter fw = null;
                String str = null;
                int count = 0;
                HashSet<TreeNode> toPrune = new HashSet<TreeNode>();
                while ((str = br.readLine()) != null) {
                    String sourcename = treenames.get(fn).get(count);
                    if (sourcename.equals("TAXONOMY")) {
                        break;
                    }
                    if (first == true) {
                        first = false;
                        opened = true;
                        fw = new FileWriter(outdir + "/" + fn);
                    }
                    try {
                        Tree tr = TreeReader.readTree(str + ";");
                        for (TreeNode jn : tr.externalNodes()) {
                            String ottid = ((String) jn.getLabel()).replace("ott", "");
                            ((JadeNode) jn).setName(ottid);
                        }
                        if (tr.getRoot().getChildCount() > 1) {
                            boolean going = true;
                            while (going == true) {
                                TreeNode knuckle = null;
                                for (TreeNode jn : tr.internalNodes(NodeOrder.PREORDER)) {
                                    if (jn.getChildCount() == 1) {
                                        knuckle = jn;
                                        break;
                                    }
                                }
                                if (knuckle == null) {
                                    break;
                                } else {
                                    TreeNode par = knuckle.getParent();
                                    TreeNode tc = knuckle.getChild(0);
                                    if(tc.getChildCount()>0){
                                        for (TreeNode cc : tc.getChildren()) {
                                            knuckle.addChild(cc);
                                        }
                                        knuckle.removeChild(tc);
                                    }else{
                                        par.removeChild(knuckle);
                                        par.addChild(tc);
                                    }
                                }
                            }
                        }
                        fw.write(sourcename + " " + tr + ";\n");
                    } catch (Exception ie) {
                        System.out.println("problem reading file/tree " + fn);
                        ie.printStackTrace();
                        continue;
                    }
                    count += 1;
                }
                if (opened == true) {
                    fw.close();
                }
                fr.close();
                br.close();

            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        ProcessSubproblems ps = new ProcessSubproblems();
    }
}
