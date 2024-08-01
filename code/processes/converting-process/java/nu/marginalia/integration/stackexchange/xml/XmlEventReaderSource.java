package nu.marginalia.integration.stackexchange.xml;

import javax.xml.stream.XMLEventReader;

public interface XmlEventReaderSource {
    XMLEventReader reader();
    void close() throws Exception;
}
