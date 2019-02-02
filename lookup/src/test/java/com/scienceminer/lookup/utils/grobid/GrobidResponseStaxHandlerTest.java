package com.scienceminer.lookup.utils.grobid;

import com.ctc.wstx.stax.WstxInputFactory;
import com.scienceminer.lookup.utils.xml.StaxUtils;
import org.codehaus.stax2.XMLStreamReader2;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class GrobidResponseStaxHandlerTest {

    GrobidResponseStaxHandler target;
    WstxInputFactory inputFactory = new WstxInputFactory();

    @Before
    public void setUp() throws Exception {
        target = new GrobidResponseStaxHandler();
    }

    @Test
    public void testParsingPublication_shouldWork() throws Exception {
        InputStream inputStream = this.getClass().getResourceAsStream("/sample.grobid.xml");
        XMLStreamReader2 reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(inputStream);

        StaxUtils.traverse(reader, target);

        GrobidResponseStaxHandler.GrobidResponse structures = target.getResponse();

        System.out.println(structures.getAtitle());
        System.out.println(structures.getFirstAuthor());

    }
}