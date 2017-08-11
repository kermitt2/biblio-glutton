package com.scienceminer.glutton.utilities.sax;

import org.xml.sax.*;
import java.io.StringReader;

/**
 * A dumb class to neutralise the dumb XML spec/libraries. 
 * ?Avoid the SAX parser to download the externally define DTD for each document.
 */
public class DumbEntityResolver implements EntityResolver {
    public InputSource resolveEntity(String publicID, String systemID)
        throws SAXException {
        
        return new InputSource(new StringReader(""));
    }
}
