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

	public final static String simpleCleanField(String input) {
        String newInput = input.replace("\n", " ");
        newInput = newInput.replaceAll("( )+", " ").trim();
        while (newInput.startsWith("\"")) {
        	newInput = newInput.substring(1);
        }

        while (newInput.endsWith("\"")) {
        	newInput = newInput.substring(0,newInput.length()-1);
        }

        return newInput.trim();
	}

	/**
     * Remove useless punctuation at the end and beginning of a metadata field.
     * <p/>
     * Use with care !
     */
    public final static String cleanField(String input0) {
        if (input0 == null) {
            return null;
        }
        if (input0.length() == 0) {
            return null;
        }

        String input = input0.replace(",,", ",");
        input = input.replace(", ,", ",");
        int n = input.length();

        // characters at the end
        for (int i = input.length() - 1; i > 0; i--) {
            char c = input.charAt(i);
            if ((c == ',') ||
                (c == ' ') ||
                (c == '.') ||
                (c == '-') ||
                (c == '_') ||
                (c == '/') ||
                //(c == ')') ||
                //(c == '(') ||
                (c == ':')) {
                n = i;
            } else if (c == ';') {
                // we have to check if we have an html entity finishing
                if (i - 3 >= 0) {
                    char c0 = input.charAt(i - 3);
                    if (c0 == '&') {
                        break;
                    }
                }
                if (i - 4 >= 0) {
                    char c0 = input.charAt(i - 4);
                    if (c0 == '&') {
                        break;
                    }
                }
                if (i - 5 >= 0) {
                    char c0 = input.charAt(i - 5);
                    if (c0 == '&') {
                        break;
                    }
                }
                if (i - 6 >= 0) {
                    char c0 = input.charAt(i - 6);
                    if (c0 == '&') {
                        break;
                    }
                }
                n = i;
            } else 
            	break;
        }

        input = input.substring(0, n);

        // characters at the begining
        n = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == ',') ||
                (c == ' ') ||
                (c == '.') ||
                (c == ';') ||
                (c == '-') ||
                (c == '_') ||
                //(c == ')') ||
                //(c == '(') ||
                (c == ':')) {
                n = i;
            } else break;
        }

        input = input.substring(n, input.length()).trim();

        if ((input.endsWith(")")) && (input.startsWith("("))) {
            input = input.substring(1, input.length() - 1).trim();
        }

        if ((input.length() > 12) &&
            (input.endsWith("&quot;")) &&
            (input.startsWith("&quot;"))) {
            input = input.substring(6, input.length() - 6).trim();
        }

        return input.trim();
    }
}
