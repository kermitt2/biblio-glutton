package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang3.StringUtils;

import org.joda.time.Partial;

/**
 *  Static metadata characterization of publication types.
 */
public class BiblioDefinitions {
	static final char[] NAME_DELIMITERS = new char[]{'.', ',', ';', ':', '-', ' '};

	// document type
	public static final int	ARTICLE	= 0;
	public static final int	JOURNAL	= 1;
	public static final int	PROCEEDINGS	= 2;
	public static final int	COLLECTION = 3;
	public static final int	BOOK = 4;
	public static final int	ONLINE = 5;
	public static final int	REPORT = 6;
	public static final int	UNKNOWN	= -1;

	// data management attributes
	private static final List<String> DATA_MANAGEMENT = Arrays.asList("coreDate", "crossrefDate", 
																	  "coreJson", "crossrefString",
																	  "istexDate", "istexJson", 
																	  "dc:type", "repoId", "coreId", 
																	  "pmid", "pmc", "pii", 
																	  "language", "rawLanguage");

	// bibliographical item profile per bibliographical type
	// article published in a journal
	private static final List<String> ARTICLE_IN_JOURNAL = Arrays.asList("articleTitle",
																		"authors", "year", "month",
																		"day", "eyear", "emonth",
																		"eday", "title", "volume",
																		"number", "start_page",
																		"end_page", "pagination", 
																		"start_page_int", "end_page_int",
																		"editors",
																		"issn", "eissn", "doi",
																		"publisher");
	private static final List<String> ARTICLE_IN_JOURNAL_COMPACT = Arrays.asList("articleTitle",
																				"firstAuthorSurname",
																				"journal_abbrev", "volume",
																				"number", "start_page",
																				"pagination",
																				"start_page_int", "end_page_int",
																				"end_page", "day", "month",
																				"year");
	// article published in conference proceedings
	private static final List<String> ARTICLE_IN_PROCEEDINGS = Arrays.asList("articleTitle",
																			"authors", "year", "month",
																			"day", "eyear", "emonth",
																			"eday", "title", "volume",
																			"start_page", "end_page",
																			"pagination",
																			"start_page_int", "end_page_int",
																			"editors", "event_name",
																			"event_location",
																			"event_start_date",
																			"event_end_date", "doi",
																			"isbn", "publisher",
																			"publication_place");
	private static final List<String> ARTICLE_IN_PROCEEDINGS_COMPACT = Arrays.asList("articleTitle",
																						"firstAuthorSurname",
																						"title",
																						"event_start_date",
																						"event_end_date", "volume",
																						"start_page", "end_page",
																						"pagination",
																						"start_page_int", "end_page_int",
																						"day", "month", "year");
	// article published in a book
	private static final List<String> ARTICLE_IN_BOOK = Arrays.asList("articleTitle",
																		"authors", "year", "month",
																		"day", "eyear", "emonth",
																		"eday", "title",
																		"start_page", "end_page",
																		"start_page_int", "end_page_int",
																		"pagination",
																		"editors", "doi", "isbn",
																		"publisher",
																		"publication_place");
	private static final List<String> ARTICLE_IN_BOOK_COMPACT = Arrays.asList("articleTitle",
																			"firstAuthorSurname",
																			"title", "start_page", 
																			"pagination",
																			"start_page_int", "end_page_int",
																			"end_page", "day", "month",
																			"year");
	// article published in a collection (e.g. Lecture Notes in computer Sciences)
	private static final List<String> ARTICLE_IN_COLLECTION = Arrays.asList("articleTitle",
																			"authors", "year", "month",
																			"day", "eyear", "emonth",
																			"eday", "title",
																			"start_page", "end_page",
																			"pagination",
																			"start_page_int", "end_page_int",
																			"collection_title",
																			"editors", "isbn", "issn",
																			"eissn", "volume", "doi",
																			"publisher",
																			"publication_place");
	private static final List<String> ARTICLE_IN_COLLECTION_COMPACT = Arrays.asList("articleTitle",
																					"firstAuthorSurname",
																					"title",
																					"collection_title",
																					"start_page", "end_page",
																					"pagination",
																					"start_page_int", "end_page_int",
																					"day", "month", "year");

	// document published online (e.g. on a research archive or on the author's home page)
	// this format have to encompass what ever we find online, i.e.
	// to handle all types of PDF documents submitted without bibliographic data.
	// note that the URL is here important and it is different from getTargetUrl
	private static final List<String> DOCUMENT_ONLINE = Arrays.asList("articleTitle",
																	"authors", "year", "month",
																	"day", "eyear", "emonth",
																	"eday", "title",
																	"start_page", "end_page",
																	"pagination",
																	"start_page_int", "end_page_int",
																	"editors", "doi",
																	"publisher", "isbn",
																	"server_online_date",
																	"publication_place",
																	"retrieval_date",
																	"publisher", "url");
	private static final List<String> DOCUMENT_ONLINE_COMPACT = Arrays.asList("articleTitle",
																			"firstAuthorSurname",
																			"year", "month", "day",
																			"eday", "emonth", "title",
																			"start_page", "end_page",
																			"pagination",
																			"start_page_int", "end_page_int",
																			"eyear");



}