package com.scienceminer.glutton.utils.xml;

import com.scienceminer.glutton.data.*;
import com.scienceminer.glutton.utils.*;
import org.apache.commons.lang3.StringUtils;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import org.joda.time.DateTimeFieldType; 
import org.joda.time.Partial;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SAX parser for HAL TEI metadata 
 *
 * @author Patrice Lopez
 */
public class HALTEISaxHandler extends DefaultHandler {

    private Biblio biblio = null;
    private StringBuilder accumulator = new StringBuilder(); // Accumulate parsed text

    // current fields
    private String idnoType = null;
    private String biblScopeType = null;
    private String level = null;
    private String dateType = null;
    private String foreNameType = null;
    private String authorRole = null;
    private String currentScheme = null;
    private String documentCodeType = null;

    private List<Person> authors;
    private List<Affiliation> affiliations = null;
    private String lastName = null;
    private String foreName = null;
    private String middleName = null;
    private String suffix = null;
    private String initials = null;
    private String identifier = null;
    private String identifierSource = null;
    private Identifier identifierObject = null;
    private Identifier authorIdentifierObject = null;
    private String affiliationString = null;

    private String langCode = null;

    // grant
    private List<Grant> grants = null;
    private Grant currentGrant = null;

    // position tags
    private boolean sourceDesc = false;
    private boolean analytic = false;
    private boolean monogr = false;
    private boolean abbrev = false;
    private boolean profileDesc = false;
    private boolean publicationStmt = false;

    private DateTimeFieldType[] fieldYMD = new DateTimeFieldType[] {
        DateTimeFieldType.year(),
        DateTimeFieldType.monthOfYear(),
        DateTimeFieldType.dayOfMonth(),
    };

    private DateTimeFieldType[] fieldYM = new DateTimeFieldType[] {
        DateTimeFieldType.year(),
        DateTimeFieldType.monthOfYear()
    };

    private DateTimeFieldType[] fieldY = new DateTimeFieldType[] {
        DateTimeFieldType.year()
    };

    private static volatile Pattern page = Pattern.compile("(\\d+)");

    public HALTEISaxHandler() {
    }

    public HALTEISaxHandler(Biblio b) {
        this.biblio = b;
    }

    public Biblio getBiblio() {
        return this.biblio;
    } 

    public void characters(char[] ch, int start, int length) {
        accumulator.append(ch, start, length);
    }

    public String getText() {
        return accumulator.toString().trim();
    }

    public void endElement(java.lang.String uri, java.lang.String localName, java.lang.String qName) throws SAXException {
        if (qName.equals("sourceDesc")) {
            sourceDesc = false;
        } else if (qName.equals("analytic")) {
            analytic = false;
            if ( (authors != null) && (authors.size() >0) ) {
                biblio.setAuthors(authors);
                authors = null;
            }
        } else if (qName.equals("monogr")) {
            monogr = false;
        } else if (qName.equals("profileDesc")) {
            profileDesc = false;
        } else if (qName.equals("publicationStmt")) {
            publicationStmt = false;
        } else if (qName.equals("idno") && idnoType != null) {
            if (idnoType.equals("issn")) {
                String issn = getText();
                if (issn != null && issn.length()>0)
                    biblio.setIssn(issn);
            } else if (idnoType.equals("isbn")) {
                String isbn = getText();
                if (isbn != null && isbn.length()>0)
                    biblio.setIsbn(isbn);
            } else if (idnoType.equals("doi")) {
                String doi = getText();
                if (doi != null && doi.length()>0)
                    biblio.setDoi(doi);
            } else if (idnoType.equals("halId")) {
                String halId = getText();
                if (halId != null && halId.length()>0)
                    biblio.setHalId(halId);
            } else if (idnoType.equals("halUri")) {
                String halUri = getText();
                if (halUri != null && halUri.length()>0) 
                    biblio.setHalUri(halUri);
            } else if (idnoType.equals("pubmed")) {
                String stringPmid = getText();
                if (stringPmid != null & stringPmid.length()>0) {
                    biblio.setPubmedId(stringPmid);
                    // we can try to get the numeric representation too
                    try {
                        Integer intPmid = Integer.parseInt(stringPmid);
                        if (intPmid != null)
                            biblio.setPmid(intPmid);
                    } catch(Exception e) {
                        // do nothing !
                    }
                }
            }
            idnoType = null;
        } else if (qName.equals("biblScope") && sourceDesc) {
            if (biblScopeType.equals("volume")) {
                String volume = getText();
                if ( (volume != null) && (volume.length() > 0) && !volume.equals("n/a")) 
                    biblio.setVolume(volume);
            } else if (biblScopeType.equals("issue")) {
                String issue = getText();
                if ( (issue != null) && (issue.length() > 0) && !issue.equals("n/a") ) 
                    biblio.setNumber(issue);
            } else if (biblScopeType.equals("pp") || biblScopeType.equals("page")) {
                String pagination = getText();
                if ( (pagination != null) && (pagination.length() > 0) && !pagination.equals("n/a") && !pagination.equals("n/a-n/a")) {
                    //biblio.setPagination(pagination);
                    // we want to detect start/end page when we have a pagination block
                    if ( (pagination != null) && (pagination.length() > 0) ) {
                        // first we might have a "suppl.:"" prefix
                        pagination = pagination.replace("Suppl:", "").replace("suppl:", "").replace("Suppl", "").replace("suppl", "").trim();
                        // TBD: where to capture that we are in some supplementary stuff at the level of pages?

                        String firstPage = null;
                        String lastPage = null;

                        // other weirdos 
                        pagination = pagination.replace("passim", "").trim();
                        pagination = pagination.replace("CR", "").trim();
                        pagination = pagination.replace("concl", "").trim();
                        pagination = pagination.replace("cntd", "").trim();
                        pagination = pagination.replace("discussion", "").trim();
                        pagination = pagination.replace("author reply", "").trim();

                        // format is 117-26 / 121-6 / 1173-9
                        // OR 
                        // 5911-5924 
                        // !!!
                        int ind = pagination.indexOf("-");
                        if (ind != -1) {
                            firstPage = pagination.substring(0, ind);
                            lastPage = pagination.substring(ind+1, pagination.length());
                            // we must concatenate prefix to last page
                            int size = lastPage.length();
                            if (size < firstPage.length()) {
                                lastPage = firstPage.substring(0, firstPage.length() - size) + lastPage;
                            }
                        } else {
                            // sometimes we have a comma...
                            int ind2 = pagination.indexOf(",");
                            if (ind2 != -1) {
                                firstPage = pagination.substring(0, ind2);
                                lastPage = pagination.substring(ind2+1, pagination.length());
                                // we must concatenate prefix to last page
                                int size = lastPage.length();
                                if (size < firstPage.length()) {
                                    lastPage = firstPage.substring(0, firstPage.length() - size) + lastPage;
                                }
                            } else {
                                // we assume one page article
                                firstPage = pagination;
                                lastPage = pagination;
                            }
                        }
                        //System.out.println(pagination + " -> " + firstPage + " / " + lastPage);
                        
                        if ( (firstPage != null) && (firstPage.length() > 0) ) {
                            biblio.setStartPage(firstPage);
                            int firstPageVal = -1;
                            try {
                                firstPageVal = Integer.parseInt(firstPage);
                            } catch (Exception e) {
                                // logger here
                                //System.out.println("parsing of first page failed: " + firstPage);
                            }
                            if (firstPageVal != -1)
                                biblio.setStartPageInt(firstPageVal);
                            else {
                                // set the raw pagination
                                biblio.setPagination(pagination); 

                                // try also a more shallow extraction
                                Matcher matcher = page.matcher(pagination);
                                if (matcher.find()) {
                                    if (matcher.groupCount() > 0) {
                                        firstPage = matcher.group(0);
                                        if ( (firstPage != null) && (firstPage.length() > 0) && !firstPage.equals("n/a")) {
                                            biblio.setStartPage(firstPage);
                                            try {
                                                firstPageVal = Integer.parseInt(firstPage);
                                            } catch (Exception e) {
                                                // logger here
                                                //System.out.println("parsing of first page failed: " + firstPage);
                                            }
                                            if (firstPageVal != -1)
                                                biblio.setStartPageInt(firstPageVal);
                                        }
                                    }
                                }
                            }
                        }

                        if ( (lastPage != null) && (lastPage.length() > 0) ) {
                            biblio.setEndPage(lastPage);
                            int lastPageVal = -1;
                            try {
                                lastPageVal = Integer.parseInt(lastPage);
                            } catch (Exception e) {
                                // logger here
                                //System.out.println("parsing of last page failed: " + lastPage);
                            }
                            if (lastPageVal != -1)
                                biblio.setEndPageInt(lastPageVal);
                            else {
                                // set the raw pagination
                                biblio.setPagination(pagination); 

                                // try also a more shallow extraction
                                Matcher matcher = page.matcher(pagination);
                                if (matcher.find()) {
                                    if (matcher.groupCount() > 1) {
                                        lastPage = matcher.group(1);
                                        if ( (lastPage != null) && (lastPage.length() > 0)  && !lastPage.equals("n/a")) {
                                            biblio.setEndPage(lastPage);
                                            try {
                                                lastPageVal = Integer.parseInt(lastPage);
                                            } catch (Exception e) {
                                                // logger here
                                                //System.out.println("parsing of last page failed: " + firstPage);
                                            }
                                            if (lastPageVal != -1)
                                                biblio.setEndPageInt(lastPageVal);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            biblScopeType = null;
        } else if (qName.equals("title") && sourceDesc) {
            if ("j".equals(level) && monogr) {
                // full journal title
                String title = getText();
                if ( (title != null) && (title.length() > 0) ) 
                    biblio.setTitle(title);
            } else if ("j".equals(level) && monogr && abbrev) {
                // abbreviated journal title
                String journalTitleAbbrev = getText();
                if ( (journalTitleAbbrev != null) && (journalTitleAbbrev.length() > 0) ) 
                    biblio.setJournalAbbrev(journalTitleAbbrev);
            } else if ("m".equals(level) && monogr) {
                // full journal title
                String title = getText();
                if ( (title != null) && (title.length() > 0) ) 
                    biblio.setTitle(title);
            } else if ("a".equals(level) || analytic) {
                // article title
                String articleTitle = getText();
                if ( (articleTitle != null) && (articleTitle.length() > 0) ) 
                    biblio.setArticleTitle(articleTitle);
            }

            level = null;
            abbrev = false;
        } else if (qName.equals("publisher") && sourceDesc) {
            String publisher = getText();
            if ( (publisher != null) && (publisher.length() > 0) ) 
                biblio.setPublisher(publisher);
        } else if (qName.equals("language")) {
            // full language name
            String rawLang = getText();
            if ( (rawLang != null) && (rawLang.length() > 0) ) {                
                biblio.setRawLanguage(rawLang);
            }
            // the usual ISO 639-1 should be around as attribute
            if (langCode != null)
                biblio.setLanguage(langCode);
            langCode = null;
        } else if (qName.equals("date")) {
            String dateString = getText();
            if ((dateString != null) && (dateString.length() > 0)) {
                if ("datePub".equals(dateType)) {
                    this.dateProcessing(biblio, "publication", dateString);
                }
            }
        } else if (qName.equals("abstract") && profileDesc) {
            String abstractString = getText();
            if ( (abstractString != null) && (abstractString.length() > 0) )
                biblio.setAbstract(abstractString);
        } else if (qName.equals("forename")) {
            if ("middle".equals(foreNameType))
                middleName = getText();
            else
                foreName = getText();
            foreNameType = null;
        } else if (qName.equals("surname")) {
            lastName = getText();
        } else if (qName.equals("author")) {
            Person author = new Person();
            if ( (lastName != null) && (lastName.length() > 0) )
                author.setLastName(lastName);
            if ( (foreName != null) && (foreName.length() > 0) )
                author.setFirstName(foreName);
            if ( (suffix != null) && (suffix.length() > 0) )
                author.setSuffix(suffix);
            if ( (initials != null) && (initials.length() > 0) )
                author.setInits(initials);
            if ( (middleName != null) && (middleName.length() > 0) )
                author.setMiddleName(middleName);

            if ( (authorIdentifierObject != null) && 
                 (authorIdentifierObject.getIdentifierName() != null) && 
                 (authorIdentifierObject.getIdentifierValue() != null) ) {
                author.addIdentifier(authorIdentifierObject);
            }

            if ( (affiliations != null) && (affiliations.size() > 0) )
                author.setAffiliations(affiliations);

            if (author.getLastName() != null && (authorRole == null || authorRole.equals("aut"))) {
                if (authors == null)
                    authors = new ArrayList<>();
                authors.add(author);
            }
            author = null;
            lastName = null;
            foreName = null;
            middleName = null;
            suffix = null;
            initials = null;
            authorIdentifierObject = null;
            affiliations = null;

            authorRole = null;
        } 
        /*else if (qName.equals("AffiliationInfo")) { 
            // create an affiliation to be attached to the current author
            // affiliation is raw affiliation string, consider GROBID to add more usable affiliation/address descriptions
            if ( (affiliationString != null) && (affiliationString.length() >0) ) {
                Affiliation affiliation = new Affiliation(affiliationString);
                if ( (identifierObject != null) && 
                     (identifierObject.getIdentifierName() != null) && 
                     (identifierObject.getIdentifierValue() != null) ) {
                    affiliation.addIdentifier(identifierObject);
                }
                if (affiliations == null)
                    affiliations = new ArrayList<Affiliation>();
                affiliations.add(affiliation);
            }
            inAffiliationInfo = false;
            affiliationString = null;
            identifierObject = null;
        } else if (qName.equals("Affiliation")) { 
            affiliationString = getText();
        } */
        else if (qName.equals("classCode") && profileDesc) {
            // article type: n="ART" scheme="halTypology"
            if ("halTypology".equals(currentScheme)) {
                String rawPublicationType = getText();
                if ( (rawPublicationType != null) && (rawPublicationType.length() > 0) ) {
                    biblio.setRawPublicationType(rawPublicationType);
                }
                mapDocumentType(biblio, documentCodeType);
                documentCodeType = null;
            }  
        }

        accumulator.setLength(0);
    }


    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts)
            throws SAXException {
        if (qName.equals("TEI")) {
            if (biblio == null)
                biblio = new Biblio();
        } else if (qName.equals("idno") && (sourceDesc || publicationStmt)) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("type"))
                        idnoType = value;
                }
            }
        } else if (qName.equals("biblScope") && sourceDesc) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("unit"))
                        biblScopeType = value;
                }
            }
        } else if (qName.equals("sourceDesc")) {
            sourceDesc = true;
            authors = new ArrayList<>();
            affiliations = new ArrayList<>();
        } else if (qName.equals("analytic")) {
            analytic = true;
        } else if (qName.equals("monogr")) {
            monogr = true;
        } else if (qName.equals("profileDesc")) {
            profileDesc = true;
        } else if (qName.equals("publicationStmt")) {
            publicationStmt = true;
        } else if (qName.equals("title") && sourceDesc) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("level"))
                        level = value;
                    else if (name.equals("type") && value.equals("abbrev"))
                        abbrev = true;
                    //else if (name.equals("xml:lang"))
                    //    langCode = value;
                }
            }
        } else if (qName.equals("language") && profileDesc) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("ident")) {
                        langCode = value;
                        if (langCode.length() == 3) {
                            // if it is a 3 character code
                            langCode = LangUtil.getInstance().convertISOLang32ISOLang2(langCode);
                        }
                    }
                }
            }
        } else if (qName.equals("forename") && sourceDesc) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        foreNameType = value;
                    }
                }
            }
        } else if (qName.equals("author") && sourceDesc) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("role")) {
                        authorRole = value;
                    }
                }
            }
        } else if (qName.equals("classCode") && profileDesc) {
            // scheme="mesh"
            // article type: n="ART" scheme="halTypology"
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("scheme")) {
                        currentScheme = value;
                    } else if (name.equals("n")) {
                        documentCodeType = value;
                    } 
                }  
            }
        } else if (qName.equals("date")) {
            int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        dateType = value;
                    } 
                }  
            }
        }
    }

    private void dateProcessing(Biblio biblio, String type, String rawDate) {
        // try to get the best of the date information
        String year = null;
        String month = null;
        String day = null;

        // for HAL dates are YYYY-MM-DD, publication dates are usually only YYYY
        if (rawDate == null)
            return;
        if (rawDate.length() >= 4) {
            year = rawDate;
            if (rawDate.length() > 4) {
                int ind = rawDate.indexOf("-");
                if (ind != -1) {
                    year = rawDate.substring(0, ind);
                    if (rawDate.length() >= 7) {
                        month = rawDate.substring(ind+1, ind+3);
                        if (rawDate.length() > 7) {
                            int ind2 = rawDate.indexOf("-", ind+1);
                            if (ind2 != -1) {
                                day = rawDate.substring(ind2+1, rawDate.length());
                            }
                        }
                    }
                }
            }
        }

        int yearVal = -1;
        int monthVal = -1;
        int dayVal = -1;

        /*if (month != null) {
            monthVal = Utilities.convertMedlineMonth(month);
        }*/

        if (year != null) {
            try {
                yearVal = Integer.parseInt(year);
            } catch(Exception e) {
                System.err.println("Failure to parse year: " + year);
            }
        }

        if ((monthVal == -1) && (month != null)) {
            try {       
                monthVal = Integer.parseInt(month);
            } catch(Exception e) {
                System.err.println("Failure to parse month: " + month);
            }
        }

        if (day != null) {
            try {
                dayVal = Integer.parseInt(day);
            } catch(Exception e) {
                System.err.println("Failure to parse day: " + day);
            }
        }

        Partial date = null;
        try {
            if (yearVal != -1) {
                if (monthVal != -1) {
                    if (dayVal != -1) {
                        dayVal = Utilities.correctDay(dayVal, monthVal);
                        int[] values = new int[] {yearVal, monthVal, dayVal};
                        List types = new ArrayList(Arrays.asList(fieldYMD));
                        date = new Partial(fieldYMD, values);
                    } else {
                        int[] values = new int[] {yearVal, monthVal};
                        List types = new ArrayList(Arrays.asList(fieldYM));
                        date = new Partial(fieldYM, values);
                    }
                } else {
                    int[] values = new int[] {yearVal};
                    List types = new ArrayList(Arrays.asList(fieldY));
                    date = new Partial(fieldY, values);
                }
            }
        } catch(Exception e) {
            // to log...
            System.out.println(year + " / " + month + " / " + day);
            e.printStackTrace();
        }
        if (date != null) {
            if (type.equals("publication"))
                biblio.setPublicationDate(date);
            else if (type.equals("update"))
                biblio.setLastUpdateDate(date);
        }
    }

    public void mapDocumentType(Biblio biblio, String codeType) {
        if (codeType == null) 
            return;
        if (codeType.equals("ART")) {
            biblio.setDocumentType(BiblioDefinitions.ARTICLE);
            biblio.setHostType(BiblioDefinitions.JOURNAL);
        } else if (codeType.equals("COMM")) {
            biblio.setDocumentType(BiblioDefinitions.ARTICLE);
            biblio.setHostType(BiblioDefinitions.PROCEEDINGS);
        } else if (codeType.equals("POSTER")) {
            biblio.setDocumentType(BiblioDefinitions.ARTICLE);
            biblio.setHostType(BiblioDefinitions.PROCEEDINGS);
        } else if (codeType.equals("COUV")) {
            biblio.setDocumentType(BiblioDefinitions.ARTICLE);
            biblio.setHostType(BiblioDefinitions.BOOK);
        } else if (codeType.equals("REPORT") || codeType.equals("OTHERREPORT")) {
            biblio.setDocumentType(BiblioDefinitions.REPORT);
        } else if (codeType.equals("CREPORT")) {
            biblio.setDocumentType(BiblioDefinitions.ARTICLE);
            biblio.setHostType(BiblioDefinitions.REPORT);
        } else if (codeType.equals("OUV")) {
            biblio.setDocumentType(BiblioDefinitions.BOOK);
        } else if (codeType.equals("PROCEEDINGS")) {
            biblio.setDocumentType(BiblioDefinitions.PROCEEDINGS);
        } else if (codeType.equals("ISSUE")) {
            biblio.setDocumentType(BiblioDefinitions.JOURNAL);
        } else if (codeType.equals("THESE") || codeType.equals("HDR") || codeType.equals("MEM") || codeType.equals("ETABTHESE")) {
            biblio.setDocumentType(BiblioDefinitions.REPORT);
        }    
    }
}