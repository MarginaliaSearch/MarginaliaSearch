package nu.marginalia.integration.stackexchange.xml;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

public class StackExchange7zXmlEventReaderSource implements XmlEventReaderSource {

    static {
        // We need to set this for SAX reasons.  Something to do with reading
        // XML files with more than 50,000,000 entities being forbidden to enhance
        // security somehow.  Since we're using STAX, these aren't
        // software-configurable.
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
    }

    private final XMLEventReader reader;
    private final SevenZFile postsFile;
    public StackExchange7zXmlEventReaderSource(Path pathTo7zFile, String xmlFileName)
            throws IOException, XMLStreamException
    {
        postsFile = new SevenZFile(pathTo7zFile.toFile());

        SevenZArchiveEntry postsEntry = null;

        for (SevenZArchiveEntry entry : postsFile.getEntries()) {
            if (xmlFileName.equals(entry.getName())) {
                postsEntry = entry;
                break;
            }
        }

        if (postsEntry == null) {
            postsFile.close();
            throw new FileNotFoundException("No " + xmlFileName + " in provided archive");
        }

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        reader = xmlInputFactory.createXMLEventReader(postsFile.getInputStream(postsEntry));
    }

    @Override
    public XMLEventReader reader() {
        return reader;
    }

    @Override
    public void close() throws Exception {
        reader.close();
        postsFile.close();
    }
}
