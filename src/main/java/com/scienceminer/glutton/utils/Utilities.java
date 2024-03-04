package com.scienceminer.glutton.utils;

import com.scienceminer.glutton.exception.*;

import java.util.*;
import java.io.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains some various utility static methods.  
 *
 */
public class Utilities {

    private static final Logger logger = LoggerFactory.getLogger(Utilities.class);

    private static Set<String> dates = new LinkedHashSet<String>();

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

    public static InputStream request(String request) throws MalformedURLException {
        InputStream in = null;
        long startTime = 0;
        long endTime = 0;
        startTime = System.currentTimeMillis();
        URL url = new URL(request);
        HttpURLConnection conn = getConnection(url);
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            logger.error("Can't get data stream.", e);
        }
        endTime = System.currentTimeMillis();
        logger.info("spend:" + (endTime - startTime) + " ms");
        return in;
    }

    private static final HttpURLConnection getConnection(URL url) {
        int retry = 0, retries = 4;
        boolean delay = false;
        HttpURLConnection connection = null;
        do {
            try {
                if (delay) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("accept-charset", "UTF-8");
                switch (connection.getResponseCode()) {
                    case HttpURLConnection.HTTP_OK:
                        logger.info(url + " **OK**");
                        return connection; // **EXIT POINT** fine, go on
                    case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
                        logger.info(url + ":" + connection.getResponseCode());
                        break;// retry
                    case HttpURLConnection.HTTP_UNAVAILABLE:
                        logger.info(url + "**unavailable**" + " :" + connection.getResponseCode());
                        break;// retry, server is unstable
                    default:
                        //stop
                        logger.info(url + ":" + connection.getResponseCode());
                }
                // we did not succeed with connection (or we would have returned the connection).
                connection.disconnect();
                // retry
                retry++;
                logger.warn("Failed retry " + retry + "/" + retries);
                delay = true;
                if (retry == retries) {
                    logger.debug(url + ":" + connection.getResponseCode());
                }
            } catch (IOException e) {
                logger.error(url + ": The URL does not appear reachable.", e);
            }
        } while (retry < retries);
        return connection;
    }

    // managing a list of dates for OAI-PMH
    static Calendar toDay = Calendar.getInstance();

    static int todayYear = toDay.get(Calendar.YEAR);

    static int minYear = 1900;

    static {
        int todayMonth = toDay.get(Calendar.MONTH) + 1;
        int todayDay = toDay.get(Calendar.DAY_OF_MONTH) + 1;
        //for (int year = 1960; year <= todayYear; year++) {
        for (int year = 2020; year <= todayYear; year++) {
            int monthYear = (year == todayYear) ? todayMonth : 12;
            for (int month = 1; month <= monthYear; month++) {
                for (int day = 1; day <= daysInMonth(year, month); day++) {
                    if ((year == todayYear) && (todayMonth == todayMonth) && (todayDay == day)) {
                        break;
                    }
                    StringBuilder date = new StringBuilder();
                    date.append(String.format("%04d", year));
                    date.append("-");
                    date.append(String.format("%02d", month));
                    date.append("-");
                    date.append(String.format("%02d", day));
                    getDates().add(date.toString());
                }
            }
        }
    }

    protected static int daysInMonth(int year, int month) {
        int daysInMonth;
        switch (month) {
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                daysInMonth = 31;
                break;
            case 2:
                if (((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)) {
                    daysInMonth = 29;
                } else {
                    daysInMonth = 28;
                }
                break;
            default:
                // returns 30 even for nonexistant months 
                daysInMonth = 30;
        }
        return daysInMonth;
    }

    public static boolean isValidDate(String dateString) {
        //consider other options (YY, YY-MM)?
        return dates.contains(dateString);
    }

    public static String completeDate(String date) {
        if (date.endsWith("-")) {
            date = date.substring(0, date.length()-1);
        }

        String val = "";
        if (date.length() < 4) {
            return val;
        } else if (date.length() == 4) {
            val = date + "-12-31";
        } else if ((date.length() == 7) || (date.length() == 6)) {
            int ind = date.indexOf("-");
            String monthStr = date.substring(ind + 1, date.length());
            if (monthStr.length() == 1) {
                monthStr = "0" + monthStr;
            }
            if (monthStr.equals("02")) {
                val = date.substring(0, 4) + "-" + monthStr + "-28";
            } else if ((monthStr.equals("04")) || (monthStr.equals("06")) || (monthStr.equals("09"))
                    || (monthStr.equals("11"))) {
                val = date.substring(0, 4) + "-" + monthStr + "-30";
            } else {
                int month = Integer.parseInt(monthStr);
                if (month > 12 || month < 1) {
                    monthStr = "12";
                }
                val = date.substring(0, 4) + "-" + monthStr + "-31";
            }
        } else {
            int ind = date.indexOf("-");
            int ind2 = date.lastIndexOf("-");
            String monthStr = date.substring(ind + 1, ind + 3);
            try {
                int month = Integer.parseInt(monthStr);
                if (month > 12 || month < 1) {
                    val = date.substring(0, 4) + "-12" + date.substring(ind + 3, date.length());
                }
                String dayStr = date.substring(ind2 + 1, ind2 + 3);
                int day = Integer.parseInt(dayStr);
                if (day > 31 || day < 1) {
                    val = date.substring(0, 8) + "28";// so naif i know
                }
            } catch (Exception e) {
            }
            val = date.trim();
            // we have the "lazy programmer" case where the month is 00, e.g. 2012-00-31
            // which means the month is unknown

            ///val = val.replace("-00-", "-12-");
        }
        val = val.replace(" ", "T"); // this is for the dateOptionalTime elasticSearch format 

        if (!val.matches("\\d{4}-\\d{2}-\\d{2}") || (Integer.parseInt(val.substring(0, 4)) < minYear || todayYear < Integer.parseInt(val.substring(0, 4)))) {
            val = "";
        }
        return val;
    }

    public static Set<String> getDates() {
        return dates;
    }

}
