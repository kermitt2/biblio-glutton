package com.scienceminer.glutton.data;

/**
 * Utility class, to easily handle Biblio related tasks.
 */
public final class BiblioUtils {

	private BiblioUtils() {
	}

	/**
	 * Convert ISBN10 to ISBN13, as documented on <a href="http://www.isbn-13.info/">http://www.isbn-13.info/</a>
	 *
	 * @param isbn the isbn10 to convert, assuming there are only numbers in the string
	 * @return the converted isbn13 string
	 */
	public static String isbn10to13(String isbn) {
		if (isbn.length() != 10) {
			throw new IllegalArgumentException("Can only convert ISBN10. Incorrect length (" + isbn.length()
						+ ") for: " + isbn);
		}

		String isbn13 = "978" + isbn.substring(0, 9);

		int checksum = 0;
		for (int i = 1; i <= isbn13.length(); i++) {
			int a = Integer.parseInt(String.valueOf(isbn13.charAt(i - 1)));

			if (i % 2 == 1) {
				checksum += a;
			}
			else {
				checksum += 3 * a;
			}
		}

		checksum = 10 - (checksum % 10);

		return isbn13 + String.valueOf(checksum);
	}

	/**
	 * Formats an issn String to ####-####.
	 *
	 * @param issn the issn, assuming the string contains 8 numbers
	 * @return
	 */
	public static String formatIssn(String issn) {
		if (issn == null) {
			return null;
		}

		StringBuilder builder = new StringBuilder(issn);

		if (builder.length() == 8) {
			builder.insert(4, '-');
		}

		return builder.toString();
	}

	/**
	 * Formats an ISBN String to ###-#-##-######-#.
	 *
	 * @param isbn the isbn, assuming the string contains 13 numbers
	 * @return
	 */
	public static String formatIsbn(String isbn) {
		if (isbn == null) {
			return null;
		}

		StringBuilder builder = new StringBuilder(isbn);

		if (builder.length() == 13) {
			builder.insert(12, '-');
			builder.insert(6, '-');
			builder.insert(4, '-');
			builder.insert(3, '-');
		}

		return builder.toString();
	}
}
