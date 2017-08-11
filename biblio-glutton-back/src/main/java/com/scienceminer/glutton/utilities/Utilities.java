package com.scienceminer.glutton.utilities;

import java.util.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * This class contains some various utility static methods.  
 *
 */
public class Utilities {

	/**
	 * Medline month are usually expressed with 3-letter, e.g. Jan, Sep, this method
	 * convert it into an integer, e.g. 1 for Jan, 9 for Sep. 
	 */
	public static int convertMedlineMonth(String crap) {
		if (crap == null)
			return -1;
		DateTimeFormatter format = DateTimeFormat.forPattern("MMM");
		DateTime instance = null;
		try {
 			instance = format.withLocale(Locale.ENGLISH).parseDateTime(crap);  
 		} catch (Exception e) {
 			// logger here maybe (it would log a lot of useless failures)
 			//System.out.println("Failed to parse medline month: " + crap);
 		}
    	if (instance == null)
    		return -1;
    	else
	    	return instance.getMonthOfYear();
	}

	/**
	 * Some medline dates are wrong because the day is not valid given the
	 * month: 2016 Sep 31
	 * We check here the month and day, and make it valid, e.g. we change 31 
	 * to 30 for the day if the month is September.
	 * We consider Feb has 28 days, and do not take into account the year 
	 * (actually there is no date invalid for February in the whole medline
	 * as of 2017 August)  
	 * @return valid day according to provided month
	 */
	public static int correctDay(int day, int month) {
		if ( (month == 4) || (month == 6) || (month == 9) || (month == 11) ) {
			if (day >= 31)
				return 30;
		} else if (month == 2) {
			if (day >= 29)
				return 28;
		}
		return day;
	}
}
