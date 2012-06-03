package jade.tree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TreeFileReader {
public TreeFileReader(){
	TreeReader tr = new TreeReader();
	String ts = "";
	try{
		BufferedReader br = new BufferedReader(new FileReader("/media/data/Dropbox/projects/AVATOL/graphtests/taxonomies/ncbi_no_env_samples.txt"));
		ts = br.readLine();
		br.close();
	}catch(IOException ioe){}
	tr.setTree(ts);
	JadeTree jt = tr.readTree();
}
}
