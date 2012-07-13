package opentree;

import java.util.*;

public class GeneralUtils {

	public static int sum_ints(List<Integer> list){
		if(list==null || list.size()<1)
			return 0;

		int sum = 0;
		for(Integer i: list)
			sum = sum+i;

		return sum;
	}

}
