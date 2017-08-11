package com.scienceminer.glutton.utilities;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains some various utility methods related to language.  
 *
 */
public class LangUtil {

	public static final Logger LOGGER = LoggerFactory
			.getLogger(LangUtil.class);

	private static LangUtil instance = null;

	private Map<String, Locale> localeMap = null;

	public static/* synchronized */LangUtil getInstance() {
		if (instance == null) {
			getNewInstance();
		}
		return instance;
	}

	/**
	 * Return a new instance.
	 */
	protected static synchronized void getNewInstance() {
		// GrobidProperties.getInstance();
		LOGGER.debug("synchronized getNewInstance");
		instance = new LangUtil();
	}

	/**
	 * Hidden constructor
	 */
	private LangUtil() {
		String[] languages = Locale.getISOLanguages();
		localeMap = new HashMap<String, Locale>(languages.length);
		for (String language : languages) {
    		Locale locale = new Locale(language);
    		localeMap.put(locale.getISO3Language(), locale);
		}
	}

	/**
	 * Convert three letter code language into two letter code language, or in
	 * other terms convert ISO 639-2 entry into ISO 639-1 entry
	 */
	public String convertISOLang32ISOLang2(String isoLang) {
		Locale theLocale = localeMap.get(isoLang);
		if (theLocale != null)
			return theLocale.getLanguage();
		else 
			return null;
	}
}
