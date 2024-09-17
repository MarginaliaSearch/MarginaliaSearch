package nu.marginalia.integration.stackexchange.xml;

import nu.marginalia.integration.stackexchange.xml.XmlEventReaderSource;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.StringReader;

class StringXmlTestEventReader implements XmlEventReaderSource {
    private final XMLEventReader reader;

    public StringXmlTestEventReader(String xml) throws XMLStreamException {
        reader = XMLInputFactory.newInstance().createXMLEventReader(
                new StringReader(xml)
        );
    }

    @Override
    public XMLEventReader reader() {
        return reader;
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
