package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.FunderData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * SAX parser for the Crossref Open Funder Registry RDF file (registry.rdf).
 * Parses SKOS/RDF-XML format and emits FunderData objects via a callback.
 */
public class FunderRegistryRdfReader extends DefaultHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunderRegistryRdfReader.class);

    private static final String DOI_PREFIX = "http://dx.doi.org/";
    private static final String DOI_PREFIX_HTTPS = "https://doi.org/";

    private Consumer<FunderData> consumer;
    private FunderData currentFunder;
    private StringBuilder accumulator = new StringBuilder();

    // state tracking for nested elements
    private boolean inPrefLabel = false;
    private boolean inAltLabel = false;
    private boolean inLiteralForm = false;
    private boolean inPostalAddress = false;

    public void load(InputStream is, Consumer<FunderData> consumer) {
        this.consumer = consumer;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            // disable external entities for security
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, this);
        } catch (Exception e) {
            LOGGER.error("Error parsing funder registry RDF", e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        accumulator.append(ch, start, length);
    }

    private String getText() {
        return accumulator.toString().trim();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        accumulator.setLength(0);

        if (localName.equals("Concept") || qName.endsWith(":Concept")) {
            currentFunder = new FunderData();
            String about = atts.getValue("rdf:about");
            if (about == null) {
                about = atts.getValue("", "about");
            }
            if (about != null) {
                currentFunder.setDoi(extractDoi(about));
            }
        } else if (localName.equals("prefLabel") || qName.endsWith(":prefLabel")) {
            inPrefLabel = true;
        } else if (localName.equals("altLabel") || qName.endsWith(":altLabel")) {
            inAltLabel = true;
        } else if (localName.equals("literalForm") || qName.endsWith(":literalForm")) {
            inLiteralForm = true;
        } else if (localName.equals("postalAddress") || qName.endsWith(":postalAddress")) {
            inPostalAddress = true;
        } else if (currentFunder != null) {
            if (localName.equals("broader") || qName.endsWith(":broader")) {
                String resource = atts.getValue("rdf:resource");
                if (resource == null) {
                    resource = atts.getValue("", "resource");
                }
                if (resource != null) {
                    currentFunder.addBroaderDoi(extractDoi(resource));
                }
            } else if (localName.equals("narrower") || qName.endsWith(":narrower")) {
                String resource = atts.getValue("rdf:resource");
                if (resource == null) {
                    resource = atts.getValue("", "resource");
                }
                if (resource != null) {
                    currentFunder.addNarrowerDoi(extractDoi(resource));
                }
            } else if (localName.equals("status") || qName.endsWith(":status")) {
                String resource = atts.getValue("rdf:resource");
                if (resource == null) {
                    resource = atts.getValue("", "resource");
                }
                if (resource != null && resource.contains("Inactive")) {
                    currentFunder.setStatus("Inactive");
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (localName.equals("Concept") || qName.endsWith(":Concept")) {
            if (currentFunder != null && currentFunder.getDoi() != null) {
                consumer.accept(currentFunder);
            }
            currentFunder = null;
        } else if (localName.equals("prefLabel") || qName.endsWith(":prefLabel")) {
            inPrefLabel = false;
        } else if (localName.equals("altLabel") || qName.endsWith(":altLabel")) {
            inAltLabel = false;
        } else if (localName.equals("literalForm") || qName.endsWith(":literalForm")) {
            if (currentFunder != null && inLiteralForm) {
                String text = getText();
                if (!text.isEmpty()) {
                    if (inPrefLabel) {
                        currentFunder.setName(text);
                    } else if (inAltLabel) {
                        currentFunder.addAltName(text);
                    }
                }
            }
            inLiteralForm = false;
        } else if (localName.equals("postalAddress") || qName.endsWith(":postalAddress")) {
            inPostalAddress = false;
        } else if (currentFunder != null) {
            String text = getText();
            if (!text.isEmpty()) {
                if (localName.equals("fundingBodyType") || qName.endsWith(":fundingBodyType")) {
                    currentFunder.setFundingBodyType(text);
                } else if (localName.equals("fundingBodySubType") || qName.endsWith(":fundingBodySubType")) {
                    currentFunder.setFundingBodySubType(text);
                } else if (localName.equals("region") || qName.endsWith(":region")) {
                    currentFunder.setRegion(text);
                } else if (localName.equals("addressCountry") || qName.endsWith(":addressCountry")) {
                    if (inPostalAddress) {
                        currentFunder.setCountry(text);
                    }
                }
            }
        }

        accumulator.setLength(0);
    }

    private String extractDoi(String url) {
        if (url.startsWith(DOI_PREFIX)) {
            return url.substring(DOI_PREFIX.length());
        } else if (url.startsWith(DOI_PREFIX_HTTPS)) {
            return url.substring(DOI_PREFIX_HTTPS.length());
        }
        return url;
    }
}
