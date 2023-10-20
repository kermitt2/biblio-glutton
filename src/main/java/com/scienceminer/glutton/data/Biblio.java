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
 *  A generic loosy data model for bibliographical data.
 */
public class Biblio implements Serializable {

	// to ensure unique ID for multi-threaded object creation
	static AtomicInteger nextId = new AtomicInteger();
	private final int id; // int is OK for 2 billion values

	// document type is givne by the type of the target object and the type of its host 
	private int	documentType = BiblioDefinitions.UNKNOWN;
	private int	hostType = BiblioDefinitions.UNKNOWN;

	// bibliographical attributes
	private final Map<String, BiblioAttribute> attributes = new TreeMap<String, BiblioAttribute>();

	// classification information
	private final List<ClassificationClass> classifications = new ArrayList<ClassificationClass>();

	// targets information - only the first target (as ranked by the link resolver) is kept
	private String publisherWebSite;
	private List<String> fullTextUrls;

	// the URL given by the primary publisher in the official DOI record at CrossRef
	private String doiPublisherUrl;

	// list of grants
	private List<Grant> grants;

	// list of structured keyword objects (in contrast to a raw list of keywords as attribute field)
	private List<Keyword> keywords;

	private List<Reference> references = null;

	// TBD
	// erroneous biblio item
	private boolean error = false;

	public static String dateInDisplayFormat(Partial date) {
		String result = DateUtils.formatDayMonthYear(date);
		return result == null ? "" : result;
	}

	public static String dateISODisplayFormat(Partial date) {
		String result = DateUtils.formatYearMonthDay(date);
		return result == null ? "" : result;
	}

	public static String duplicateDateInDisplayFormat(String date) {
		String result = DateUtils.formatDuplicateOrdinalDate(date);
		return result == null ? "" : result;
	}

	public static String issnInDisplayFormat(String issn) {
		String result = BiblioUtils.formatIssn(issn);
		return result == null ? "" : result;
	}

	public static String isbnInDisplayFormat(String isbn) {
		String result = BiblioUtils.formatIsbn(isbn);
		return result == null ? "" : result;
	}

	public Biblio() {
		this.id = nextId.incrementAndGet();
	}

	public Biblio(int documentType, int hostType) {
		this.id = nextId.incrementAndGet();
		this.documentType = documentType;
		this.hostType = hostType;
	}

	/**
	 * Return the unique immutable identifier of the biblio object
	 */
	public int getId() {
		return this.id;
	}

	protected void setPublisherAttribute(String key, Object value) {
		BiblioAttribute att = attributes.get(key);
		if (att != null) {
			att.setAttributeValue(value);
			attributes.put(key, att);
		}
		else {
			BiblioAttribute att2 = BiblioAttribute.createPublisherAttribute(key, value);
			attributes.put(key, att2);
		}
	}

	public Object getAttributeValue(String attributeName) {
		BiblioAttribute att = attributes.get(attributeName);
		return att != null ? att.getAttributeValue() : null;
	}

	public BiblioAttribute getAttribute(String attributeName) {
		return attributes.get(attributeName);
	}

	public String getPublisherWebSite() {
		return publisherWebSite;
	}

	public void setPublisherWebSite(String publisherWebSite) {
		this.publisherWebSite = publisherWebSite;
	}

	public List<String> getFullTextUrls() {
		return fullTextUrls;
	}

	public void setFullTextUrls(List<String> targetUrls) {
		this.fullTextUrls = targetUrls;
	}

	public void addFullTextUrl(String targetUrl) {
		if (this.fullTextUrls == null)
			this.fullTextUrls = new ArrayList<String>();
		this.fullTextUrls.add(targetUrl);
	}

	public String getBestFullTextUrl() {
		if ( (this.fullTextUrls == null) || (this.fullTextUrls.size() == 0) )
			return null;
		else
			return fullTextUrls.get(0);
	}

	public String getDOIPublisherUrl() {
		return doiPublisherUrl;
	}

	public void setDOIPublisherUrl(String doiPublisherUrl) {
		this.doiPublisherUrl = doiPublisherUrl;
	}

	public String getArticleTitle() {
		return (String) getAttributeValue("articleTitle");
	}

	public void setArticleTitle(String value) {
		setPublisherAttribute("articleTitle", value);
	}

	public String getLanguage() {
		return (String) getAttributeValue("language");
	}

	public void setLanguage(String language) {
		setPublisherAttribute("language", language);
	}

	public String getRawLanguage() {
		return (String) getAttributeValue("rawLanguage");
	}

	public void setRawLanguage(String language) {
		setPublisherAttribute("rawLanguage", language);
	}

	public String getDoi() {
		return (String) getAttributeValue("doi");
	}

	public void setDoi(String doi) {
		doi = doi.replace("https://doi.org/", "");
		doi = doi.replace("http://doi.org/", "");
		doi = doi.replace("doi.org/", "");
		setPublisherAttribute("doi", StringUtils.trim(doi));
	}

	public String getRawPublicationType() {
		return (String) getAttributeValue("rawPublicationType");
	}

	public void setRawPublicationType(String rawPublicationType) {
		setPublisherAttribute("rawPublicationType", StringUtils.trim(rawPublicationType));
	}

	public String getPublicationTypeUI() {
		return (String) getAttributeValue("publicationTypeUI");
	}

	public void setPublicationTypeUI(String publicationTypeUI) {
		setPublisherAttribute("publicationTypeUI", StringUtils.trim(publicationTypeUI));
	}

	public void setCoreId(Integer coreId) {
		setPublisherAttribute("coreId", coreId);
	}

	public Integer getCoreId() {
		return (Integer) getAttributeValue("coreId");
	}

	public void setPmid(Integer pmid) {
		setPublisherAttribute("pmid", pmid);
	}

	public Integer getPmid() {
		return (Integer) getAttributeValue("pmid");
	}

	public void setPmc(String pmc) {
		setPublisherAttribute("pmc", pmc);
	}

	public String getPmc() {
		return (String) getAttributeValue("pmc");
	}

	public void setPii(String pii) {
		setPublisherAttribute("pii", pii);
	}

	public String getPii() {
		return (String) getAttributeValue("pii");
	}

	public void setRepoId(Integer repoId) {
		setPublisherAttribute("repoId", repoId);
	}

	public Integer getRepoId() {
		return (Integer) getAttributeValue("repoId");
	}

	public String getPubmedId() {
		return (String) getAttributeValue("pubmedid");
	}

	public void setPubmedId(String pubmedId) {
		// string might be empty
		if (pubmedId != null && pubmedId.trim().length() == 0)
			return;

		// case ID given as "PMID: 34189422"
		if (pubmedId != null && (pubmedId.startsWith("PMID:") || pubmedId.startsWith("pmid:"))) {
			pubmedId = pubmedId.replace("PMID:", "");
			pubmedId = pubmedId.replace("pmid:", "");
		}

		// case "pubmed.ncbi.nlm.nih.gov/34133859/" or "https://pubmed.ncbi.nlm.nih.gov/25886103/" 
		// or "http://www.ncbi.nlm.nih.gov/pmc/articles/pmc7813351/" or "www.ncbi.nlm.nih.gov/pubmed/"
		if (pubmedId != null && (pubmedId.startsWith("pubmed.ncbi.nlm.nih.gov/") || 
			pubmedId.startsWith("https://pubmed.ncbi.nlm.nih.gov/"))) {
			pubmedId = pubmedId.replace("https://pubmed.ncbi.nlm.nih.gov/", "");
			pubmedId = pubmedId.replace("pubmed.ncbi.nlm.nih.gov/", "");
			pubmedId = pubmedId.replace("www.ncbi.nlm.nih.gov/pubmed/", "");
		}
		if (pubmedId != null && (pubmedId.startsWith("http://www.ncbi.nlm.nih.gov/pmc/articles/pmc") ||
			 pubmedId.startsWith("https://www.ncbi.nlm.nih.gov/pmc/articles/pmc"))) {
			pubmedId = pubmedId.replace("http://www.ncbi.nlm.nih.gov/pmc/articles/", "");
			pubmedId = pubmedId.replace("https://www.ncbi.nlm.nih.gov/pmc/articles/", "");
		}

		//case ID ends with "/", which is quite common
		if (pubmedId != null && pubmedId.endsWith("/")) {
			pubmedId = pubmedId.replace("/", "");
		}

		// sometimes it's a DOI 
		if (pubmedId != null && (pubmedId.startsWith("10.") || pubmedId.startsWith("doi.org/10.")) && pubmedId.indexOf("/") != -1) {
			if (getDoi() == null) {
				setDoi(pubmedId);
				return;
			}
		}

		// sometimes it's a PMC ID "PMC2655142"
		if (pubmedId != null && (pubmedId.startsWith("PMC") || pubmedId.startsWith("pmc"))) {
			if (getPmc() == null) {
				setPmc(pubmedId);
				return;
			}
		}

		// other weird quite common case "22642955DOI"
		if (pubmedId != null && pubmedId.endsWith("DOI")) {
			pubmedId = pubmedId.replace("DOI", "");
		}

		setPublisherAttribute("pubmedid", StringUtils.trim(pubmedId));
	}

	public String getPublisher() {
		return (String) getAttributeValue("publisher");
	}

	public void setPublisher(String publisher) {
		setPublisherAttribute("publisher", publisher);
	}

	public int getDocumentType() {
		return documentType;
	}

	public void setDocumentType(int documentType) {
		this.documentType = documentType;
	}

	public Partial getPublicationDate() {
		return (Partial) getAttributeValue("publicationDate");
	}

	/**
	 * Returns the earliest of the printed and the electronic publication dates.
	 * If the two dates have the same root, but one is more specific, then the more specific
	 * one is returned (i.e. 2001-02-01 will be returned in favour of 2001-02)
	 */
	public Partial getOldestPublicationDate() {
		Partial printed = getPublicationDate();
		Partial electronic = getEPublicationDate();

		if (printed == null) {
			return electronic;
		}
		if (electronic == null) {
			return printed;
		}

		int[] fieldsPrinted = printed.getValues();
		int[] fieldsElectronic = electronic.getValues();

		// the fields are returned largest to smallest.
		for (int i = 0; i < Math.min(fieldsPrinted.length, fieldsElectronic.length); i++) {
			if (fieldsPrinted[i] != fieldsElectronic[i]) {
				return fieldsPrinted[i] < fieldsElectronic[i] ? printed : electronic;
			}
		}

		// if we got this far, either they are an exact match (in which case it doesn't matter which we return),
		// or one is more specific than the other, in which case we return the most detailed.
		return fieldsPrinted.length > fieldsElectronic.length ? printed : electronic;
	}

	public void setPublicationDate(Partial date) {
		setPublisherAttribute("publicationDate", date);
	}

	public Partial getEPublicationDate() {
		return (Partial) getAttributeValue("ePublicationDate");
	}

	public void setEPublicationDate(Partial date) {
		setPublisherAttribute("ePublicationDate", date);
	}

	public Partial getLastUpdateDate() {
		return (Partial) getAttributeValue("lastUpdateDate");
	}

	public void setLastUpdateDate(Partial date) {
		setPublisherAttribute("lastUpdateDate", date);
	}

	public String getTitle() {
		return (String) getAttributeValue("title");
	}

	public void setTitle(String title) {
		setPublisherAttribute("title", title);
	}

	public String getJournalAbbrev() {
		return (String) getAttributeValue("journal_abbrev");
	}

	public void setJournalAbbrev(String abbrev) {
		setPublisherAttribute("journal_abbrev", abbrev);
	}

	public String getVolume() {
		return (String) getAttributeValue("volume");
	}

	public void setVolume(String volume) {
		setPublisherAttribute("volume", volume);
	}

	public String getStartPage() {
		return (String) getAttributeValue("start_page");
	}

	public void setStartPage(String page) {
		setPublisherAttribute("start_page", page);
	}

	public String getEndPage() {
		return (String) getAttributeValue("end_page");
	}

	public void setEndPage(String page) {
		setPublisherAttribute("end_page", page);
	}

	public Integer getStartPageInt() {
		return (Integer) getAttributeValue("start_page_int");
	}

	public void setStartPageInt(Integer page) {
		setPublisherAttribute("start_page_int", page);
	}

	public Integer getEndPageInt() {
		return (Integer) getAttributeValue("end_page_int");
	}

	public void setEndPageInt(Integer page) {
		setPublisherAttribute("end_page_int", page);
	}

	public Integer getReferenceCount() {
		if (references != null)
			return references.size();
		else 
			return null;
	}

	public void addReference(Reference reference) {
		if (this.references == null) {
			this.references = new ArrayList<>();
		}
		this.references.add(reference);
	}

	public void setReference(List<Reference> theReferences) {
		this.references = theReferences;
	}

	public List<Reference> getReferences() {
		return this.references;
	}

	public String getPagination() {
		return (String) getAttributeValue("pagination");
	}

	public void setPagination(String page) {
		setPublisherAttribute("pagination", page);
	}


	public int getHostType() {
		return hostType;
	}

	public void setHostType(int hostType) {
		this.hostType = hostType;
	}

	public String getIssn() {
		return (String) getAttributeValue("issn");
	}

	public void setIssn(String issn) {
		setPublisherAttribute("issn", StringUtils.replace(issn, "-", ""));
	}

	public String getEIssn() {
		return (String) getAttributeValue("eissn");
	}

	public void setEIssn(String eissn) {
		setPublisherAttribute("eissn", StringUtils.replace(eissn, "-", ""));
	}

	public String getNumber() {
		return (String) getAttributeValue("number");
	}

	public void setNumber(String number) {
		setPublisherAttribute("number", number);
	}

	public String getIsbn() {
		return (String) getAttributeValue("isbn13");
	}

	public void setIsbn(String isbn13) {
		String isbn = StringUtils.replace(isbn13, "-", "");

		if (isbn.length() == 10) {
			isbn = BiblioUtils.isbn10to13(isbn);
		}

		setPublisherAttribute("isbn13", isbn);
	}

	public void setAuthors(List<Person> authors) {
		if ( (authors == null) || (authors.size() == 0) )
			return;
		else {
			setFirstAuthor(authors.get(0));
			for (int i=1; i<authors.size(); i++) {
				addCoAuthor(authors.get(i));
			}
		}
	}

	public void addCoAuthor(Person newAuthor) {
		if (isSame(newAuthor, getFirstAuthor())) {
			// don't add again if this is the first author
			return;
		}

		List<Person> authors = getCoAuthors();

		boolean present = isPresent(newAuthor, authors);

		if (!present) {
			authors.add(newAuthor);
		}

		if (!newAuthor.isEmptyPerson()) {
			stripEmptyAuthors();
		}
	}

	private void stripEmptyAuthors() {
		CollectionUtils.filter(getCoAuthors(), new Predicate() {

			public boolean evaluate(Object arg0) {
				Person person = (Person) arg0;
				return !person.isEmptyPerson();
			}
		});
	}

	@SuppressWarnings("unchecked")
	public List<Person> getCoAuthors() {
		List<Person> authors = (List<Person>) getAttributeValue("authors");

		if (authors == null) {
			authors = new ArrayList<Person>();
			setPublisherAttribute("authors", authors);
		}

		return authors;
	}

	@SuppressWarnings("unchecked")
	public List<Person> getAuthors() {
		List<Person> allAuthors = new ArrayList<>();

		Person firstAuthor = getFirstAuthor(false);
		if (firstAuthor != null) 
			allAuthors.add(firstAuthor);

		List<Person> authors = (List<Person>) getAttributeValue("authors");
		if (authors != null) {
			allAuthors.addAll(authors);
		}

		return allAuthors;
	}

	public Person getFirstAuthor() {
		return getFirstAuthor(false);
	}

	/** Returns the first author, creating an entry if it isn't already present. */
	protected Person getFirstAuthor(boolean createIfNotPresent) {
		Person person = (Person) getAttributeValue("firstAuthor");
		if (person == null && createIfNotPresent) {
			person = new Person();
			setFirstAuthor(person);
		}
		return person;
	}

	public void setFirstAuthor(Person person) {
		setPublisherAttribute("firstAuthor", person);
	}

	public void setCoAuthors(List<Person> authors) {
		setPublisherAttribute("authors", authors);
	}

	public String getCoAuthorsAsString() {
		return (String) getAttributeValue("coAuthors");
	}

	/** 
	 * Returns a string containing the formatted list of authors (not including the first).
	 * @param generate is this is set, generate the string if it was not already present.
	 * @return
	 */
	public String getCoAuthorsAsString(boolean generate) {
		String result = getCoAuthorsAsString();
		if (StringUtils.isBlank(result) && generate) {
			result = generateAdditionalAuthorsOrEditorsString(getCoAuthors());
		}
		return result;
	}

	public void setCoAuthorsAsString(String authors) {
		setPublisherAttribute("coAuthors", Person.normalizeName(authors));
	}

	public String getEditorsAsString() {
		return (String) getAttributeValue("editorsAsString");
	}

	public String getEditorsAsString(boolean generate) {
		String result = (String) getAttributeValue("editorsAsString");
		if (StringUtils.isEmpty(result) && generate) {
			result = printEditorList();
		}
		return result;
	}

	public void setEditorsAsString(String editors) {
		setPublisherAttribute("editorsAsString", editors);
	}

	public void addEditor(Person editor) {
		List<Person> editors = getEditors();
		boolean present = isPresent(editor, editors);

		if (!present) {
			editors.add(editor);
		}

		setPublisherAttribute("editors", editors);
	}

	protected boolean isPresent(Person testPerson, List<Person> persons) {
		for (Person person : persons) {
			if (isSame(person, testPerson)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSame(Person person, Person testPerson) {
		if (person == null || person.getLastName() == null || testPerson == null) {
			return false;
		}

		if (person.getLastName().equals(testPerson.getLastName())) {
			if (person.getFirstName() != null) {
				return person.getFirstName().equals(testPerson.getFirstName());
			}
			else if (person.getInits() != null) {
				return person.getInits().equals(testPerson.getInits());
			}
			return true;
		}
		return false;

	}

	@SuppressWarnings("unchecked")
	public List<Person> getEditors() {
		List<Person> editors = (List<Person>) getAttributeValue("editors");
		if (editors == null) {
			editors = new ArrayList<Person>();
			setEditors(editors);
		}
		return editors;
	}

	public void setEditors(List<Person> editors) {
		setPublisherAttribute("editors", editors);
	}

	public String getAbstract() {
		return (String) getAttributeValue("abstractBlock");
	}

	public void setAbstract(String abstractBlock) {
		setPublisherAttribute("abstractBlock", abstractBlock);
	}

	public String getKeywords() {
		return (String) getAttributeValue("keywords");
	}

	public void setKeywords(String keywords) {
		setPublisherAttribute("keywords", keywords);
	}

	public List<Keyword> getKeywordItems() {
		return keywords;
	}

	public void setKeywordItems(List<Keyword> keywords) {
		this.keywords = keywords;
	}

	public void addKeywordItems(Keyword keyword) {
		if (this.keywords == null)
			this.keywords = new ArrayList<>();
		this.keywords.add(keyword);
	}

	public String getCollectionTitle() {
		return (String) getAttributeValue("collection_title");
	}

	public void setCollectionTitle(String collectionTitle) {
		setPublisherAttribute("collection_title", collectionTitle);
	}

	public String getEventName() {
		return (String) getAttributeValue("event_name");
	}

	public void setEventName(String name) {
		setPublisherAttribute("event_name", name);
	}

	public String getEventLocation() {
		return (String) getAttributeValue("event_location");
	}

	public void setEventLocation(String location) {
		setPublisherAttribute("event_location", location);
	}

	public Partial getEventStartDate() {
		return (Partial) getAttributeValue("event_start_date");
	}

	public void setEventStartDate(Partial startDate) {
		setPublisherAttribute("event_start_date", startDate);
	}

	public Partial getEventEndDate() {
		return (Partial) getAttributeValue("event_end_date");
	}

	public void setEventEndDate(Partial date) {
		setPublisherAttribute("event_end_date", date);
	}

	public Partial getServerOnlineDate() {
		return (Partial) getAttributeValue("server_online_date");
	}

	public void setServerOnlineDate(Partial date) {
		setPublisherAttribute("server_online_date", date);
	}

	public Partial getRetrievalDate() {
		return (Partial) getAttributeValue("retrieval_date");
	}

	public void setRetrievalDate(Partial date) {
		setPublisherAttribute("retrieval_date", date);
	}

	public String getPublicationPlace() {
		return (String) getAttributeValue("publication_place");
	}

	public void setPublicationPlace(String place) {
		setPublisherAttribute("publication_place", place);
	}

	public String getUrl() {
		return (String) getAttributeValue("url");
	}

	public void setUrl(String url) {
		setPublisherAttribute("url", url);
	}

	public boolean getIsSinglePage() {
		return StringUtils.isBlank(getStartPage()) || StringUtils.isBlank(getEndPage())
					|| StringUtils.equals(getStartPage(), getEndPage());
	}

	public String getPageRange() {
		/*if (StringUtils.isNotBlank(getStartPage()) && StringUtils.isNotBlank(getEndPage())
					&& getStartPage().equals(getEndPage())) {
			return getStartPage();
		}*/
		if (StringUtils.isNotBlank(getStartPage()) && StringUtils.isNotBlank(getEndPage())) {
			return getStartPage() + "-" + getEndPage();
		}
		if (StringUtils.isNotBlank(getStartPage())) {
			return getStartPage();
		}
		if (StringUtils.isNotBlank(getEndPage())) {
			return getEndPage();
		}
		return "";
	}

	public void setFirstAuthorSurname(String name) {
		getFirstAuthor(true).setLastName(Person.normalizeName(name));
	}

	public void setFirstAuthorMiddleName(String name) {
		getFirstAuthor(true).setMiddleName(Person.normalizeName(name));
	}

	public void setFirstAuthorFirstName(String name) {
		getFirstAuthor(true).setFirstName(Person.normalizeName(name));
	}

	public List<Grant> getGrants() {
		return grants;
	}

	public void setGrants(List<Grant> grants) {
		this.grants = grants;
	}

	public void addGrant(Grant grant) {
		if (this.grants == null) {
			this.grants = new ArrayList<>();
		}
		this.grants.add(grant);
	}

	/** Returns the page range. */
	public String printPages() {
		if (StringUtils.isNotBlank(getPageRange())) {
			String prefix = getIsSinglePage() ? "Page" : "Pages";
			return prefix + " " + getPageRange();
		}
		return "";
	}

	/** Prints the conference dates for display. */
	public String printConferenceDates() {
		String result = null;
		if (getEventStartDate() != null) {
			result = dateInDisplayFormat(getEventStartDate());
		}

		if (getEventEndDate() != null) {
			if (StringUtils.isNotEmpty(result)) {
				result += " - ";
			}
			result += dateInDisplayFormat(getEventEndDate());
		}
		return result;
	}

	/** Returns the article title, or 'No Title' if not set. */
	public String printArticleTitle() {
		return StringUtils.isNotBlank(getArticleTitle()) ? getArticleTitle() : "No Title";
	}

	/** Returns the name of the first author. */
	public String printFirstAuthor() {
		Person firstAuthor = getFirstAuthor();
		return firstAuthor == null ? "" : StringUtils.trimToEmpty(firstAuthor.getLastName());
	}

	/** Returns a comma separated String of the author names. */
	public String printAuthorList() {
		return printAuthorOrEditors(getFirstAuthor(), getCoAuthors(), getCoAuthorsAsString());
	}

	/** Returns a comma separated String of the editor names. */
	public String printEditorList() {
		return printAuthorOrEditors(null, getEditors(), getEditorsAsString());
	}

	private String printAuthorOrEditors(Person first, List<Person> list, String flatList) {
		String result = first != null ? first.printFullName() : "";

		if (StringUtils.isNotBlank(flatList)) {
			if (StringUtils.isNotBlank(result)) {
				result += ", ";
			}
			result += flatList;
		}
		else if (!list.isEmpty()) {
			if (StringUtils.isNotBlank(result)) {
				result += ", ";
			}
			result += generateAdditionalAuthorsOrEditorsString(list);
		}

		return result;
	}

	public List<ClassificationClass> getClassifications() {
		return this.classifications;
	}

	public void addClassifications(List<ClassificationClass> newClasses) {
		for(ClassificationClass classification : newClasses)
			addClass(classification);
	}

	/** Add a classification class */ 
	public void addClass(ClassificationClass theClass) {
		classifications.add(theClass);
	}

	/** Generates a comma separated string from all entries except the first. */
	@SuppressWarnings("rawtypes")
	private String generateAdditionalAuthorsOrEditorsString(List<Person> people) {
		Collection formatted = CollectionUtils.collect(people, new Transformer() {
			public Object transform(Object input) {
				return ((Person) input).printFullName();
			}
		});
		return StringUtils.join(formatted, ", ");
	}

	/** Correct fields of the first Biblio item based on the second one. */
	public void correct(Biblio source) {
		if (source == null) {
			return;
		}

		defaultPropertyIfNull(this, source, "hostType");
		defaultPropertyIfNull(this, source, "documentType");
		defaultPropertyIfNull(this, source, "doi");
		defaultPropertyIfNull(this, source, "pii");
		defaultPropertyIfNull(this, source, "pmc");	
		defaultPropertyIfNull(this, source, "pubmedid");
		defaultPropertyIfNull(this, source, "title");
		defaultPropertyIfNull(this, source, "firstAuthor");
		defaultPropertyIfNull(this, source, "volume");
		defaultPropertyIfNull(this, source, "number");
		defaultPropertyIfNull(this, source, "startPage");
		defaultPropertyIfNull(this, source, "endPage");
		defaultPropertyIfNull(this, source, "publicationPlace");
		defaultPropertyIfNull(this, source, "publisher");
		defaultPropertyIfNull(this, source, "articleTitle");
		defaultPropertyIfNull(this, source, "collectionTitle");
		defaultPropertyIfNull(this, source, "journalAbbrev");
		defaultPropertyIfNull(this, source, "issn");
		defaultPropertyIfNull(this, source, "EIssn");
		defaultPropertyIfNull(this, source, "isbn");
		defaultPropertyIfNull(this, source, "abstract");
		defaultPropertyIfNull(this, source, "keywords");
		defaultPropertyIfNull(this, source, "eventName");
		defaultPropertyIfNull(this, source, "eventLocation");
		defaultPropertyIfNull(this, source, "fullTextUrl");
		defaultPropertyIfNull(this, source, "publisherWebSite");

		if (source.getCoAuthors().size() >= getCoAuthors().size()) {
			setCoAuthors(source.getCoAuthors());
		}

		if (source.getEditors().size() >= getEditors().size()) {
			setEditors(source.getEditors());
		}

		// DOI linking is not represented as bibliographical attribute but as a special variable
		if (source.getDOIPublisherUrl() != null) {
			setDOIPublisherUrl(source.getDOIPublisherUrl());
		}

		if (getPublicationDate() == null) {
			setPublicationDate(source.getPublicationDate());
		}
		else if (source.getPublicationDate() != null
					&& source.getPublicationDate().size() >= getPublicationDate().size()) {
			setPublicationDate(source.getPublicationDate());
		}

		if (getEventStartDate() == null && source.getEventStartDate() != null) {
			setEventStartDate(source.getEventStartDate());
		}

		if (getEventEndDate() == null && source.getEventEndDate() != null) {
			setEventEndDate(source.getEventEndDate());
		}
	}

	/** Given that nearly everything here is using a hash map, would be far cleaner to use that instead of reflection. */
	private static void defaultPropertyIfNull(Biblio target, Biblio source, String propertyName) {
		try {
			Object srcValue = PropertyUtils.getProperty(source, propertyName);

			if (srcValue != null) {
				PropertyUtils.setProperty(target, propertyName, srcValue);
			}
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException("Unable to access property " + propertyName);
		}
		catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to access property " + propertyName);
		}
		catch (NoSuchMethodException e) {
			throw new RuntimeException("No getter/setter for property " + propertyName);
		}
	}

}