package com.scienceminer.lookup.utils.xml;

import org.codehaus.stax2.XMLStreamReader2;

public interface StaxParserContentHandler {

    void onStartDocument(XMLStreamReader2 reader);

    void onEndDocument(XMLStreamReader2 reader);

    void onStartElement(XMLStreamReader2 reader);

    void onEndElement(XMLStreamReader2 reader);

    void onCharacter(XMLStreamReader2 reader);
}