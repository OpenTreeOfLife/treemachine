package opentree;

import java.util.*;

/* 
 * Is this used anywhere?
 * 
 * Is so, what are the properties of the list being passed in?
 * 1. Is it guaranteed to start at 1 (or 0)?
 * 2. Is it consecutive from start to length(list)?
 * 
 */

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
