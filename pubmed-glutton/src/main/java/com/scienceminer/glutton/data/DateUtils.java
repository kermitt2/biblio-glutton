package com.scienceminer.glutton.data;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeFieldType;
import org.joda.time.LocalDate;
import org.joda.time.Partial;

/**
 * Utility class to handle all {@link Partial} date related tasks regarding the Biblio class.
 *
 * @see Partial
 */
public final class DateUtils {

	/** The standard three fields used for constructing a date - YEAR, MONTH, DAY. */
	public static final DateTimeFieldType[]	YEAR_MONTH_DAY	= {DateTimeFieldType.year(),
			DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};

	private DateUtils() {
	}

	/** Prints a partial date in dd-MM-yyyy format, where dd and MM are optional. */
	public static String formatDayMonthYear(Partial date) {
		if (date == null) {
			return null;
		}

		StringBuilder builder = new StringBuilder(12);

		if (date.isSupported(DateTimeFieldType.year())) {
			builder.append(date.get(DateTimeFieldType.year()));

			if (date.isSupported(DateTimeFieldType.monthOfYear())) {
				builder.insert(0, '-');
				builder.insert(0,
							StringUtils.leftPad(String.valueOf(date.get(DateTimeFieldType.monthOfYear())), 2, '0'));

				if (date.isSupported(DateTimeFieldType.dayOfMonth())) {
					builder.insert(0, '-');
					builder.insert(0,
								StringUtils.leftPad(String.valueOf(date.get(DateTimeFieldType.dayOfMonth())), 2, '0'));
				}
			}
		}
		return builder.toString();
	}

	/** To convert a partial date to a full java.util.Date, we need to fill in any missing fields. */
	public static Date asDate(Partial partialDate) {
		if (partialDate == null || !partialDate.isSupported(DateTimeFieldType.year())) {
			return null;
		}
		int year = partialDate.get(DateTimeFieldType.year());
		int month = partialDate.isSupported(DateTimeFieldType.monthOfYear()) ? partialDate.get(DateTimeFieldType
					.monthOfYear()) : 1;
		int day = partialDate.isSupported(DateTimeFieldType.dayOfMonth()) ? partialDate.get(DateTimeFieldType
					.dayOfMonth()) : 1;

		LocalDate dateTime = new LocalDate(year, month, day);
		return dateTime.toDateTimeAtStartOfDay().toDate();
	}

	/** Builds a date from DMY format, where day and month are optional. */
	public static Partial buildDate(String day, String month, String year) {
		if (StringUtils.isNotBlank(year)) {
			Partial date = new Partial();
			date = date.with(DateTimeFieldType.year(), Integer.valueOf(year));

			if (StringUtils.isNotBlank(month)) {
				date = date.with(DateTimeFieldType.monthOfYear(), Integer.valueOf(month));

				if (StringUtils.isNotBlank(day)) {
					date = date.with(DateTimeFieldType.dayOfMonth(), Integer.valueOf(day));
				}
			}
			return date;
		}

		return null;
	}

	/** Builds a date from dd-MM-yyyy format, where day and month are optional. */
	public static Partial partialFromDMYDateString(String date) {
		if (StringUtils.isBlank(date)) {
			return null;
		}

		try {
			switch (date.length()) {
				case 4 :
					// yyyy
					return buildDate(null, null, date);
				case 7 :
					// MM-yyyy
					return buildDate(null, date.substring(0, 2), date.substring(3));
				case 10 :
					// dd-MM-yyyy
					return buildDate(date.substring(0, 2), date.substring(3, 5), date.substring(6));
				default :
					break;
			}

			return null;
		}
		catch (NumberFormatException e) {
			// we can not handle this exception to anything better
			return null;
		}
	}

	/** Builds a date from yyyy-MM-dd format, where day and month are optional. */
	public static Partial partialFromYMDDateString(String date) {
		if (StringUtils.isBlank(date)) {
			return null;
		}

		try {
			switch (date.length()) {
				case 4 :
					// yyyy
					return buildDate(null, null, date);
				case 7 :
					// yyyy-MM
					return buildDate(null, date.substring(5), date.substring(0, 4));
				case 10 :
					// yyyy-MM-dd
					return buildDate(date.substring(8), date.substring(5, 7), date.substring(0, 4));
				default :
					return null;
			}
		}
		catch (NumberFormatException e) {
			// we can not handle this exception to anything better
			return null;
		}
	}

	/**Formats the date of the duplicate found in NPL master*/
	public static String formatDuplicateOrdinalDate(String date) {
		if (date == null) {
			return null;
		}
		else if (date.matches("\\d{4}-00(-00)?")) {
			return date.substring(0, 4);
		}
		else if (date.matches("\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])")) {
			return date.substring(8, 10) + "-" + date.substring(5, 7) + "-" + date.substring(0, 4);
		}
		else {
			return date;
		}
	}
}
