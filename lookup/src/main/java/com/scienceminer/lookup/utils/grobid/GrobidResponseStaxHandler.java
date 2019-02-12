package com.scienceminer.lookup.utils.grobid;

import com.scienceminer.lookup.utils.xml.StaxParserContentHandler;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.trim;

/**
 * Created by lfoppiano on 29/08/16.
 */
public class GrobidResponseStaxHandler implements StaxParserContentHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(GrobidResponseStaxHandler.class);

    StackTags stackTags = new StackTags();
    StringBuffer accumulator = new StringBuffer();

    private GrobidResponse response = new GrobidResponse();

    private int indentLevel = 0;

    private boolean fetchText = false;
    private boolean firstAuthorArticle = false;
    private boolean firstAuthorMonograph = false;
    private StaxTag title = new StaxTag("title", "/biblStruct/analytic/title");

    private StaxTag firstAuthorSurname = new StaxTag("surname", "/biblStruct/analytic/author/persName/surname");
    private StaxTag firstAuthorForename = new StaxTag("forename", "/biblStruct/analytic/author/persName/forename");
    private StaxTag firstAuthorSurnameMonograph = new StaxTag("surname", "/biblStruct/monogr/author/persName/surname");
    private StaxTag firstAuthorForenameMonograph = new StaxTag("forename", "/biblStruct/monogr/author/persName/forename");

    @Override
    public void onStartDocument(XMLStreamReader2 reader) {
    }


    @Override
    public void onEndDocument(XMLStreamReader2 reader) {
        if (indentLevel > 0) LOGGER.error("something is broken!!");
    }

    @Override
    public void onStartElement(XMLStreamReader2 reader) {
        final String localName = reader.getName().getLocalPart();
        stackTags.append(localName);
        final StaxTag currentTag = new StaxTag(localName, stackTags.toString());
        if (currentTag.equals(title) &&
                (getAttributeValue(reader, "level").equals("a") &&
                        getAttributeValue(reader, "type").equals("main"))
        ) {
            fetchText = true;
        } else if (currentTag.equals(firstAuthorForename) &&
                getAttributeValue(reader, "type").equals("first")) {
            firstAuthorArticle = true;
        } else if (currentTag.equals(firstAuthorSurname) && firstAuthorArticle) {
            fetchText = true;
        } else if (currentTag.equals(firstAuthorForenameMonograph) &&
                getAttributeValue(reader, "type").equals("first")) {
            firstAuthorMonograph = true;
        } else if (currentTag.equals(firstAuthorSurnameMonograph) && firstAuthorMonograph) {
            fetchText = true;
        }

        indentLevel++;
    }

    @Override
    public void onEndElement(XMLStreamReader2 reader) {
        final String localName = reader.getName().getLocalPart();
        final StaxTag currentTag = new StaxTag(localName, stackTags.toString());
        if (fetchText) {
            if (title.equals(currentTag)) {
                response.setAtitle(accumulator.toString());
                accumulator = new StringBuffer();
            } else if (firstAuthorForename.equals(currentTag)) {
                accumulator = new StringBuffer();
            } else if(firstAuthorSurname.equals(currentTag)) {
                response.setFirstAuthor(accumulator.toString());
                accumulator = new StringBuffer();
                firstAuthorArticle = false;
            } else if (firstAuthorForenameMonograph.equals(currentTag)) {
                accumulator = new StringBuffer();
            } else if(firstAuthorSurnameMonograph.equals(currentTag)) {
                response.setFirstAuthorMonograph(accumulator.toString());
                accumulator = new StringBuffer();
                firstAuthorMonograph = false;
            }
            fetchText = false;
        }

        stackTags.peek();
        indentLevel--;
    }

    @Override
    public void onCharacter(XMLStreamReader2 reader) {
        String text = getText(reader);
        if (isEmpty(text)) {
            return;
        }

        if (fetchText) accumulator.append(text);
    }

    private String getText(XMLStreamReader2 reader) {
        String text = reader.getText();
        text = trim(text);
        return text;
    }

    private String getAttributeValue(XMLStreamReader reader, String attributeName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (attributeName.equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }

        return "";
    }

    private String extractTagContent(XMLEventReader reader, XMLEventWriter writer) throws XMLStreamException {
        XMLEvent event = reader.nextEvent();
        String data = event.asCharacters().getData();
        data = data != null ? data.trim() : "";
        writer.add(event);
        return data;
    }

    protected class StaxTag {

        private String tagName;
        private String path;

        StaxTag(String tagName, String path) {
            this.tagName = tagName;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StaxTag staxTag = (StaxTag) o;

            if (!path.equals(staxTag.path)) {
                return false;
            }

            if (!Objects.equals(tagName, staxTag.tagName)) {
                return false;
            }

            return true;
        }

        @Override
        public String toString() {
            return String.format("%s, %s", tagName, path);
        }


    }

    private class StackTags {

        private List<String> stackTags = new ArrayList<>();

        public void append(String tag) {
            stackTags.add(tag);
        }

        public String peek() {
            return stackTags.remove(stackTags.size() - 1);
        }

        public String toString() {
            return "/" + StringUtils.join(stackTags, "/");
        }

    }


    public class GrobidResponse {

        private String atitle;

        private String firstAuthor;

        private String firstAuthorMonograph;

        public GrobidResponse(String firstAuthor, String atitle) {
            this.firstAuthor = firstAuthor;
            this.atitle = atitle;
        }

        public GrobidResponse() {

        }

        public String getFirstAuthor() {
            return firstAuthor;
        }

        public void setFirstAuthor(String firstAuthor) {
            this.firstAuthor = firstAuthor;
        }

        public String getAtitle() {
            return atitle;
        }

        public void setAtitle(String atitle) {
            this.atitle = atitle;
        }

        public void setFirstAuthorMonograph(String firstAuthorMonograph) {
            this.firstAuthorMonograph = firstAuthorMonograph;
        }

        public String getFirstAuthorMonograph() {
            return firstAuthorMonograph;
        }
    }

    public GrobidResponse getResponse() {
        return response;
    }
}
