package com.scienceminer.glutton.utilities.sax;

import com.scienceminer.glutton.data.*;
import com.scienceminer.glutton.utilities.*;
import org.apache.commons.lang3.StringUtils;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import org.joda.time.DateTimeFieldType; 
import org.joda.time.Partial;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SAX parser for XML MedlineSax files. 
 *
 * @author Patrice Lopez
 */
public class MedlineSaxHandler extends DefaultHandler {

    private Biblio biblio = null;
    private List<Biblio> biblios = null;
    private StringBuilder accumulator = new StringBuilder(); // Accumulate parsed text

    // current fields

    // mesh
    private String chemicalUI = null;
    private String chemicalNameOfSubstance = null;
    private String chemicalRegistryNumber = null;

    private String descriptorUI = null;
    private String qualifierUI = null;
    private String descriptorName = null;
    private String qualifierName = null;
    private boolean majorTopicDescriptor = false;
    private boolean majorTopicQualifier = false;

    // biblio
    private Integer pmid = null;
    private String issne = null;
    private String issnp = null;
    private String volume = null;
    private String issue = null;
    private String title = null;
    private String journalTitleAbbrev = null;
    private String year = null;
    private String month = null;
    private String day = null;
    private String firstPage = null;
    private String lastPage = null;
    private String articleTitle = null;

    private String lastName = null;
    private String foreName = null;
    private String suffix = null;
	private String initials = null;
	private String authorIdentifier = null;

	private String language = null;

    // position tags
    private boolean pubDate = false;
    private boolean electronic = false;
    private boolean print = false;

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

    public MedlineSaxHandler() {
    }

    public MedlineSaxHandler(List<Biblio> b) {
        biblios = b;
    }

	public List<Biblio> getBiblios() {
		return biblios;
	} 

    public void characters(char[] ch, int start, int length) {
        accumulator.append(ch, start, length);
    }

    public String getText() {
        return accumulator.toString().trim();
    }

    public void endElement(java.lang.String uri, java.lang.String localName, java.lang.String qName) throws SAXException {
    	if (qName.equals("PMID")) {
    		Integer pmid = null;
    		String rawPmid = getText();
    		try {
    			pmid = Integer.parseInt(rawPmid);
    		} catch(Exception e) {
    			System.out.println("Error parsing the PMID: " + rawPmid);
    			e.printStackTrace();
    		}
    		biblio.setPmid(pmid);
    	} else if (qName.equals("PubmedArticle")) {
    		if (biblios == null) {
    			biblios = new ArrayList<Biblio>();
    		}
    		biblios.add(biblio);
    	} else if (qName.equals("ISSN")) {
    		if (electronic)
    			issne = getText();
    		else  if (print)
    			issnp = getText();
    		electronic = false;
    		print = false;

    		if ( (issne != null) && (issne.length() > 0) ) {
    			biblio.setEIssn(issne);
    		} else if ( (issnp != null) && (issnp.length() > 0) ) {
    			biblio.setIssn(issnp);
    		}

    		issne = null;
    		issnp = null;
    	} else if (qName.equals("Volume")) {
    		volume = getText();
    		if ( (volume != null) && (volume.length() > 0) ) 
    			biblio.setVolume(volume);
    		volume = null;
    	} else if (qName.equals("Issue")) {
    		issue = getText();
    		if ( (issue != null) && (issue.length() > 0) ) 
    			biblio.setNumber(issue);
    		issue = null;
    	} else if (qName.equals("Title")) {
    		// full journal title
    		title = getText();
    		if ( (title != null) && (title.length() > 0) ) 
    			biblio.setTitle(title);
    		title = null;
    	} else if (qName.equals("ISOAbbreviation")) {
    		// abbreviated journal title
    		journalTitleAbbrev = getText();
    		if ( (journalTitleAbbrev != null) && (journalTitleAbbrev.length() > 0) ) 
    			biblio.setJournalAbbrev(journalTitleAbbrev);
    		journalTitleAbbrev = null;
    	} else if (qName.equals("ArticleTitle")) {
    		// article title
    		articleTitle = getText();
    		if ( (articleTitle != null) && (articleTitle.length() > 0) ) 
    			biblio.setArticleTitle(articleTitle);
    		articleTitle = null;
    	} else if (pubDate && qName.equals("Year")) {
    		year = getText();
    	} else if (pubDate && qName.equals("Month")) {
    		month = getText();
    	} else if (pubDate && qName.equals("Day")) {
    		day = getText();
    	} else if (qName.equals("Chemical")) {
    		// store the chemical
    		MeSHClass mesh = new MeSHClass();
    		mesh.setChemicalNameOfSubstance(chemicalNameOfSubstance);
    		mesh.setChemicalRegistryNumber(chemicalRegistryNumber);
    		mesh.setChemicalUI(chemicalUI);

    		biblio.addClass(mesh);

    		chemicalUI = null;
			chemicalNameOfSubstance = null;
			chemicalRegistryNumber = null;
        } else if (qName.equals("MeshHeading")) {
			MeSHClass mesh = new MeSHClass();
    		mesh.setDescriptorUI(descriptorUI);
    		mesh.setDescriptorName(descriptorName);
    		mesh.setQualifierUI(qualifierUI);
    		mesh.setQualifierName(qualifierName);
    		mesh.setMajorTopicDescriptor(majorTopicDescriptor);
    		mesh.setMajorTopicQualifier(majorTopicQualifier);

    		biblio.addClass(mesh);

    		descriptorUI = null;
    		qualifierUI = null;
    		descriptorName = null;
    		qualifierName = null;
    		majorTopicDescriptor = false;
    		majorTopicQualifier = false;
		} else if (qName.equals("RegistryNumber")) {
			chemicalRegistryNumber = getText();
		} else if (qName.equals("NameOfSubstance")) {
			chemicalNameOfSubstance = getText();
		} else if (qName.equals("DescriptorName")) {
			 descriptorName = getText();
		} else if (qName.equals("QualifierName")) {
			 qualifierName = getText();
		} else if (qName.equals("MedlinePgn")) {
			// this is the pagination type used in practice currently
			String pagination = getText(); 
			if ( (pagination != null) && (pagination.length() > 0) ) {
				// first we might have a "suppl.:"" prefix
				pagination = pagination.replace("Suppl:", "").replace("suppl:", "").replace("Suppl", "").replace("suppl", "").trim();
				// TBD: where to capture that we are in some supplenmentary stuff at the level of pages?

				// other weirdos 
				pagination = pagination.replace("passim", "").trim();
				pagination = pagination.replace("CR", "").trim();
				pagination = pagination.replace("concl", "").trim();
				pagination = pagination.replace("cntd", "").trim();
				pagination = pagination.replace("discussion", "").trim();
				pagination = pagination.replace("author reply", "").trim();

				// format is 117-26 / 121-6 / 1173-9
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
			                    if ( (firstPage != null) && (firstPage.length() > 0) ) {
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
			                    if ( (lastPage != null) && (lastPage.length() > 0) ) {
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
			pagination = null;
		} else if (qName.equals("StartPage")) {
			// this pagination is not used now but it is supposed to be used in the future
			firstPage = getText();
			int firstPageVal = -1;
			try {
				firstPageVal = Integer.parseInt(firstPage);
			} catch (Exception e) {
				// logger here
				//System.out.println("parsing of first page failed: " + firstPage);
			}
			if ( (firstPage != null) && (firstPage.length() > 0) )
				biblio.setStartPage(firstPage);
			if (firstPageVal != -1)
				biblio.setStartPageInt(firstPageVal);
			firstPage = null;
		} else if (qName.equals("EndPage")) {
			// this pagination is not used now but it is supposed to be used in the future
			lastPage = getText();
			int lastPageVal = -1;
			try {
				lastPageVal = Integer.parseInt(lastPage);
			} catch (Exception e) {
				// logger here
				//System.out.println("parsing of last page failed: " + lastPage);
			}
			if ( (lastPage != null) && (lastPage.length() > 0) )
				biblio.setEndPage(lastPage);
			if (lastPageVal != -1)
				biblio.setEndPageInt(lastPageVal);
			lastPage = null;
		} else if (qName.equals("Language")) {
			// that's a three letter code - so possibly ISO 639-2 ?
			String rawLang = getText();
			if ( (rawLang != null) && (rawLang.length() > 0) ) {
				//  "und" is used for undetermined
				if (!rawLang.equals("und")) {
					// convert into usual ISO 639-1
					String langCode = LangUtil.getInstance().convertISOLang32ISOLang2(rawLang);
					if (langCode != null)
						biblio.setLanguage(langCode);
					else 
						biblio.setRawLanguage(rawLang);
				}
			}
		} else if (qName.equals("PubDate")) {
        	pubDate = false;

        	// try to get the best of the date information
    		int yearVal = -1;
    		int monthVal = -1;
    		int dayVal = -1;

    		if (month != null) {
    			monthVal = Utilities.convertMedlineMonth(month);
    		}

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
    		if (date != null)	
			    biblio.setPublicationDate(date);

        	year = null;
        	month = null;
        	day = null;
        } else if (qName.equals("LastName")) {
        	lastName = getText();
        } else if (qName.equals("ForeName")) {
        	foreName = getText();
        } else if (qName.equals("Suffix")) {
        	suffix = getText();
        } else if (qName.equals("Initials")) {
        	initials = getText();
        } else if (qName.equals("Identifier")) {
        	authorIdentifier = getText();
        } else if (qName.equals("Author")) {
        	Person author = new Person();
        	
        } 


        accumulator.setLength(0);
    }


    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts)
            throws SAXException {
        if (qName.equals("PubmedArticle")) {
        	biblio = new Biblio();
        } else if (qName.equals("NameOfSubstance")) {
        	int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) & (value != null)) {
                    if (name.equals("UI")) {
                        chemicalUI = value;
                    }
                }
            }
        } else if (qName.equals("DescriptorName")) {
        	int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) & (value != null)) {
                    if (name.equals("UI")) {
                        descriptorUI = value;
                    } else if (name.equals("MajorTopicYN")) {
                    	if (value.equals("Y"))
	                        majorTopicDescriptor = true;
                    }
                }
            }
        } else if (qName.equals("QualifierName")) {
        	int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) & (value != null)) {
                    if (name.equals("UI")) {
                        qualifierUI = value;
                    } else if (name.equals("MajorTopicYN")) {
                    	if (value.equals("Y"))
	                        majorTopicQualifier = true;
                    }
                }
            }
        } else if (qName.equals("PubDate")) {
        	pubDate = true;
        } if (qName.equals("ISSN")) {
        	int length = atts.getLength();

            // Process each attribute
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) & (value != null)) {
                    if (name.equals("IssnType")) {
                    	if (value.equals("Print"))	
                    		print = true;
                    	else if (value.equals("Electronic"))	
                        	electronic = true;
                    }
                }
            }
        }
    }
}