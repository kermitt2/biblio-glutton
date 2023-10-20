package com.scienceminer.glutton.harvester;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.exception.*;
import com.scienceminer.glutton.utils.Utilities;

/**
 * Extract and parse records from OAI-PMH response.
 *
 * @author Achraf
 */
public class HALOAIPMHDomParser {

    protected static final Logger logger = LoggerFactory.getLogger(HALOAIPMHDomParser.class);

    private final static String source = Harvester.Source.HAL.getName();

    private Document doc;
    private String token;
    private XPath xPath;

    public HALOAIPMHDomParser() {
        xPath = XPathFactory.newInstance().newXPath();
    }

    /*
    ** Collectes Biblio objects from the inputStream, and saves the metadata.
    */
    public List<Biblio> getGrabbedObjects(InputStream in) {
        List<Biblio> biblioobjs = new ArrayList<Biblio>();
        setDoc(parse(in));
        if (doc != null) {
            Element rootElement = doc.getDocumentElement();
            NodeList listRecords = getRecords(rootElement);

            //XPath xPath = XPathFactory.newInstance().newXPath();//play with it  
            //NodeList nodes = (NodeList)xPath.evaluate("/OAI-PMH/ListRecords/record/metadata", rootElement, XPathConstants.NODESET);
            setToken(rootElement);
            logger.info("\t \t " + listRecords.getLength() + " records found. processing...");

            if (listRecords.getLength() >= 1) {
                for (int i = listRecords.getLength() - 1; i >= 0; i--) {
                    if ((listRecords.item(i) instanceof Element)) {
                        Element record = (Element) listRecords.item(i);

                        Element metadata = (Element) record.getElementsByTagName(OAIPMHPathsItf.TeiElement).item(0);
                        NodeList metadataNodes = metadata.getChildNodes();
                        for (int o = metadataNodes.getLength() - 1; o >= 0; o--) {
                            if ((metadataNodes.item(o) instanceof Element)) {
                                Element element = (Element) metadataNodes.item(o);
                                element.setAttribute("xmlns:tei", "http://www.tei-c.org/ns/1.0");//BOF
                                //HAL OAI PMH adds a tei namespace before each tag
                                renameNode(element);

                                Biblio biblio = processRecord(element);
                                if (biblio != null) {
                                    biblioobjs.add(biblio);
                                }
                            }
                        }
                    }
                }

            }
        }
        return biblioobjs;
    }

    public Biblio processRecord(Element record) {
        Biblio biblioObj = null;

        String type = getPublicationType(record);
        if (isConsideredType(type.split("_")[0])) {
            String repositoryDocId = getRepositoryDocId(record);
            String currentVersion = getCurrentVersion(record);
//            String docVersion = Utilities.getVersionFromURI(completeRepositoryDocId);
            //if not the current version normally we don't need the update.
//            if (docVersion.equals(currentVersion)) {
            String tei = getTei(record);
            biblioObj = new Biblio(null, source, repositoryDocId, tei);
            biblioObj.setRepositoryDocId(repositoryDocId);
            biblioObj.setRepositoryDocVersion(currentVersion);
            biblioObj.setDoi(getDoi(record));

            biblioObj.setPublicationType(type);
            biblioObj.setDomains(getDomains(record));

            //biblioObj.setPdf(getFile(record, repositoryDocId, currentVersion, biblioObj.getDoi(), type));

            biblioObj.setMetadataURL("https://hal.archives-ouvertes.fr/" + repositoryDocId + "/tei");

            logger.info("\t \t \t tei of " + repositoryDocId + " extracted.");
        }
        return biblioObj;
    }

    public String getCurrentVersion(Node record) {
        String currentVersion = null;
        try {
            Element node = (Element) xPath.compile(OAIPMHPathsItf.EditionElement).evaluate(record, XPathConstants.NODE);
            currentVersion = node.getAttribute("n");
        } catch (DataException | XPathExpressionException ex) {
            logger.info("\t \t \t \t No current edition found .");
        }
        return currentVersion;
    }

    public String getRef(Node ref) {
        String reference = null;
        Node node = null;
        try {
            node = (Node) xPath.compile(OAIPMHPathsItf.RefPATH).evaluate(ref, XPathConstants.NODE);
            if (node != null) {
                reference = node.getTextContent();
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException | DOMException ex) {
            logger.info("\t \t \t \t hal ref not found");
        }
        return reference;
    }

    public String getDoi(Node ref) {
        String doi = null;
        try {
            Node node = (Node) xPath.compile(OAIPMHPathsItf.DoiPATH).evaluate(ref, XPathConstants.NODE);
            if (node != null) {
                doi = node.getTextContent();
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException | DOMException ex) {
            logger.info("\t \t \t \t doi not found");
        }
        return doi;
    }

    public List<String> getDomains(Node ref) {
        List<String> domains = new ArrayList<String>();
        try {
            NodeList nodes = (NodeList) xPath.compile(OAIPMHPathsItf.DomainsPATH).evaluate(ref, XPathConstants.NODESET);
            if (nodes != null) {
                if (nodes.getLength() >= 1) {
                    for (int i = nodes.getLength() - 1; i >= 0; i--) {
                        if ((nodes.item(i) instanceof Element)) {
                            domains.add(((Element) nodes.item(i)).getAttribute("n") + "_" + ((Element) nodes.item(i)).getTextContent());
                        }
                    }
                }
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException | DOMException ex) {
            logger.info("\t \t \t \t no publication type found");
        }
        return domains;
    }

    public String getPublicationType(Node ref) {
        String type = null;
        try {
            Element node = (Element) xPath.compile(OAIPMHPathsItf.PublicationTypePATH).evaluate(ref, XPathConstants.NODE);
            if (node != null) {
                type = node.getAttribute("n") + "_" + node.getTextContent();
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException | DOMException ex) {
            logger.info("\t \t \t \t no publication type found");
        }
        return type;
    }

    public String getToken() {
        return this.token;
    }

    private void setToken(Element rootElement) {
        try {
            this.token = URLEncoder.encode(rootElement.getElementsByTagName(OAIPMHPathsItf.ResumptionToken).item(0).getTextContent(), StandardCharsets.UTF_8.toString());
        } catch (Exception ex) {
            this.token = null;
        }
    }

    private void setDoc(Document doc) {
        this.doc = doc;
    }

    private Document parse(InputStream in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(in);
        } catch (DataException | IOException | ParserConfigurationException | SAXException e) {//
            logger.error("Could not parse document because "
                    + e.getMessage());
        }
        return null;
    }

    /*public BinaryFile getFile(Node record, String repositoryDocId, String repositoryDocVersion, String doi, String type) {
        BinaryFile file = null;
        try {
            Element node = (Element) xPath.compile(OAIPMHPathsItf.FileElement).evaluate(record, XPathConstants.NODE);
            if (node != null) {

                String url = node.getAttribute("target");
                Element dateNode = (Element) node.getElementsByTagName("date").item(0);
                String embargoDate = "";
                if (dateNode != null) {
                    embargoDate = dateNode.getAttribute("notBefore");
                }
                file = new BinaryFile(source, url, repositoryDocId, doi, type, "application/pdf", repositoryDocId + ".pdf", repositoryDocVersion, "", embargoDate);
                file.setIsAnnexFile(false);
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException ex) {
            logger.info("\t \t \t \t No file attached .");
        }
        return file;
    }*/

    /*public List<BinaryFile> getAnnexes(Node record, String repositoryDocId, String repositoryDocVersion, String doi, String type) {
        List<BinaryFile> annexes = new ArrayList<BinaryFile>();
        NodeList nodes = null;
        try {
            nodes = (NodeList) xPath.compile(OAIPMHPathsItf.AnnexesUrlsElement).evaluate(record, XPathConstants.NODESET);
        } catch (XPathExpressionException ex) {
            logger.info("\t \t \t \t No annex files attached .");
        }
        String url = null;
        String embargoDate = null;
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            if ((nodes.item(i) instanceof Element)) {
                Element node = (Element) nodes.item(i);
                url = node.getAttribute("target");
                embargoDate = ((Element) node.getElementsByTagName("date").item(0)).getAttribute("notBefore");
                BinaryFile annex = new BinaryFile(source, url, repositoryDocId, doi, type, "", "", repositoryDocVersion, "", embargoDate);
                annex.setIsAnnexFile(true);
                annexes.add(annex);
            }
        }
        return annexes;
    }*/

    public String getTei(Node tei) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        String teiString = sb.append(innerXmlToString(tei)).toString();
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        Document teiDoc = null;
        try {
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            teiDoc = docBuilder.parse(new ByteArrayInputStream(teiString.getBytes()));
        } catch (SAXException | ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }

//        teiDoc = createTEICorpus(teiDoc);
//
//        Utilities.generateIDs(teiDoc);
        try {
            teiString = toString(teiDoc);
        } catch (DataException de) {
            de.printStackTrace();
        }
        return teiString;
    }

    private void renameNode(Element teiElement) {
        if (teiElement.getTagName().contains(":")) {
            doc.renameNode(teiElement, null, teiElement.getTagName().split(":")[1]);
            NodeList nodes = teiElement.getChildNodes();
            for (int i = nodes.getLength() - 1; i >= 0; i--) {
                if (nodes.item(i) instanceof Element) {
                    Element element = (Element) nodes.item(i);
                    renameNode(element);
                }
            }
        }
    }

    public String getRepositoryDocId(Node record) {
        String repositoryDocId = null;
        try {
            Element node = (Element) xPath.compile(OAIPMHPathsItf.IdElementPath).evaluate(record, XPathConstants.NODE);
            if (node != null) {
                repositoryDocId = node.getTextContent();
            } else {
                throw new DataException();
            }
        } catch (DataException | XPathExpressionException | DOMException ex) {
            logger.info("\t \t \t \t no publication repository id found");
        }
        return repositoryDocId;
    }

    public String getDocumentType(NodeList sets) {
        String type = null;
        for (int i = sets.getLength() - 1; i >= 0; i--) {
            String content = sets.item(i).getTextContent();
            if (content.contains("type")) {
                String[] n = content.split(":");
                type = n.length > 1 ? n[1] : null;
                break;
            }
        }
        return type;
    }

    private NodeList getRecords(Element rootElement) {
        return rootElement.getElementsByTagName(OAIPMHPathsItf.RecordElement);
    }

    private boolean isConsideredType(String setSpec) {
        try {
            OAIPMHPathsItf.ConsideredTypes.valueOf(setSpec);
            return true;
        } catch (NullPointerException | IllegalArgumentException ex) {
            return false;
        }
    }

    private static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (IllegalArgumentException | TransformerException ex) {
            throw new DataException("Error converting to String", ex);
        }
    }

    private static String innerXmlToString(Node node) {
        DOMImplementationLS lsImpl
                = (DOMImplementationLS) node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
        LSSerializer lsSerializer = lsImpl.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("xml-declaration", false);
        NodeList childNodes = node.getChildNodes();
        StringBuilder sb = new StringBuilder();
        //for (int i = 0; i < childNodes.getLength(); i++) {
            sb.append(lsSerializer.writeToString(node));
        //}
        return sb.toString();
    }
}
