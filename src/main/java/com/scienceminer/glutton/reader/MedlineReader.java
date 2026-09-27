package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.utils.xml.DumbEntityResolver;
import com.scienceminer.glutton.utils.xml.MedlineSaxHandler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads one file of the MEDLINE/PubMed distribution, a baseline file or a file of updates, both
 * being a {@code PubmedArticleSet} in XML.
 *
 * The records are handed over one by one as they are read, a file holding 30,000 of them. A file
 * of updates also names the records that were withdrawn, which are returned once it is read.
 */
public class MedlineReader {

    /**
     * @param input  the XML, already decompressed
     * @param closure called with each record of the file, in the order of the file
     * @return the PMID of the records the file says to delete, empty for a baseline file
     */
    public List<Integer> load(InputStream input, Consumer<Biblio> closure) throws IOException {
        MedlineSaxHandler handler = new MedlineSaxHandler(closure);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(false);
            // the files name a DTD at NCBI, which is neither needed nor to be fetched per file
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            parser.getXMLReader().setEntityResolver(new DumbEntityResolver());
            parser.parse(input, handler);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Not a readable MEDLINE/PubMed file: " + e.getMessage(), e);
        }
        return handler.getDeletedPmids();
    }
}
