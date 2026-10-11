package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.FunderData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Reads the Crossref Open Funder Registry, {@code registry.rdf}: SKOS in RDF/XML, one
 * {@code skos:Concept} per funder, its DOI in {@code rdf:about}. Hands each funder to a callback
 * as it is read, so the 90 MB file is never held in memory.
 *
 * What is kept of a concept: the preferred and alternative names, the country, region and type
 * of funding body, the status (Active unless the registry says Inactive or Deprecated), the
 * broader and narrower funders, and for a funder that was renamed or replaced, the DOI it was
 * replaced by. The registry's geonames links, tax identifiers and usage flags are left out.
 */
public class FunderRegistryRdfReader extends DefaultHandler {

    private static final String[] DOI_PREFIXES = { "http://dx.doi.org/", "https://dx.doi.org/",
            "https://doi.org/", "http://doi.org/" };

    private Consumer<FunderData> consumer;
    private FunderData currentFunder;
    private final StringBuilder accumulator = new StringBuilder();

    // where in a concept the text being read belongs
    private boolean inPrefLabel;
    private boolean inAltLabel;
    private boolean inLiteralForm;
    private boolean inPostalAddress;

    /**
     * @throws IOException when the file cannot be read or is not the registry; a failure used to
     *         be logged and the load reported as complete without the funders
     */
    public void load(InputStream is, Consumer<FunderData> consumer) throws IOException {
        this.consumer = consumer;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            // the registry carries no entities, and a file that does is not the registry
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, this);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("The file could not be read as the funder registry (RDF/XML)", e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        accumulator.append(ch, start, length);
    }

    private String text() {
        return accumulator.toString().trim();
    }

    private static String resource(Attributes atts) {
        String resource = atts.getValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
        return (resource != null) ? resource : atts.getValue("rdf:resource");
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) {
        accumulator.setLength(0);
        switch (localName) {
            case "Concept":
                currentFunder = new FunderData();
                String about = atts.getValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about");
                if (about == null) {
                    about = atts.getValue("rdf:about");
                }
                if (about != null) {
                    currentFunder.setDoi(extractDoi(about));
                }
                break;
            case "prefLabel":
                inPrefLabel = true;
                break;
            case "altLabel":
                inAltLabel = true;
                break;
            case "literalForm":
                inLiteralForm = true;
                break;
            case "postalAddress":
                inPostalAddress = true;
                break;
            default:
                if (currentFunder == null) {
                    return;
                }
                String resource = resource(atts);
                if (resource == null) {
                    return;
                }
                switch (localName) {
                    case "broader":
                        currentFunder.addBroaderDoi(extractDoi(resource));
                        break;
                    case "narrower":
                        currentFunder.addNarrowerDoi(extractDoi(resource));
                        break;
                    case "isReplacedBy":
                    case "renamedAs":
                        currentFunder.addReplacedByDoi(extractDoi(resource));
                        break;
                    case "status":
                        // http://data.crossref.org/fundingdata/vocabulary/Inactive, or Deprecated
                        currentFunder.setStatus(resource.substring(resource.lastIndexOf('/') + 1));
                        break;
                    default:
                        break;
                }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (localName) {
            case "Concept":
                if (currentFunder != null && currentFunder.getDoi() != null) {
                    consumer.accept(currentFunder);
                }
                currentFunder = null;
                break;
            case "prefLabel":
                inPrefLabel = false;
                break;
            case "altLabel":
                inAltLabel = false;
                break;
            case "literalForm":
                if (currentFunder != null && inLiteralForm && !text().isEmpty()) {
                    if (inPrefLabel) {
                        currentFunder.setName(text());
                    } else if (inAltLabel) {
                        currentFunder.addAltName(text());
                    }
                }
                inLiteralForm = false;
                break;
            case "postalAddress":
                inPostalAddress = false;
                break;
            default:
                if (currentFunder != null && !text().isEmpty()) {
                    switch (localName) {
                        case "fundingBodyType":
                            currentFunder.setFundingBodyType(text());
                            break;
                        case "fundingBodySubType":
                            currentFunder.setFundingBodySubType(text());
                            break;
                        case "region":
                            currentFunder.setRegion(text());
                            break;
                        case "addressCountry":
                            if (inPostalAddress) {
                                currentFunder.setCountry(text());
                            }
                            break;
                        default:
                            break;
                    }
                }
        }
        accumulator.setLength(0);
    }

    /** The DOI out of the doi.org URL the registry gives for a funder. */
    static String extractDoi(String url) {
        for (String prefix : DOI_PREFIXES) {
            if (url.startsWith(prefix)) {
                return url.substring(prefix.length());
            }
        }
        return url;
    }
}
