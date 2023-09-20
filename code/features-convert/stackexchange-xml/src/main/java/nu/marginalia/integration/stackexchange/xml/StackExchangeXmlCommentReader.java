package nu.marginalia.integration.stackexchange.xml;

import nu.marginalia.integration.stackexchange.model.StackExchangeComment;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public class StackExchangeXmlCommentReader {
    private final Path pathTo7zFile;

    public StackExchangeXmlCommentReader(Path pathTo7zFile) {
        this.pathTo7zFile = pathTo7zFile;
    }

    public Iterator<StackExchangeComment> iterator() throws IOException, XMLStreamException {
        return iterator(new StackExchange7zXmlEventReaderSource(pathTo7zFile, "Comments.xml"));
    }

    // exposed for testability
    static Iterator<StackExchangeComment> iterator(XmlEventReaderSource source) {
        return new StackExchangeXmlIterator<>(source, StackExchangeXmlCommentReader::parseEvent);
    }

    private static final QName idName = new QName("Id");
    private static final QName postIdName = new QName("PostId");
    private static final QName textName = new QName("Text");

    private static StackExchangeComment parseEvent(XMLEvent event) {
        if (!event.isStartElement())
            return null;

        var startEvent = event.asStartElement();
        if (!"row".equals(startEvent.getName().getLocalPart()))
            return null;

        var postIdAttribute = startEvent.getAttributeByName(postIdName);
        if (postIdAttribute == null)
            return null;
        int postId = Integer.parseInt(postIdAttribute.getValue());

        var commentIdAttribute = startEvent.getAttributeByName(idName);
        if (commentIdAttribute == null)
            return null;
        int commentId = Integer.parseInt(commentIdAttribute.getValue());

        var textAttribute = startEvent.getAttributeByName(textName);
        if (textAttribute == null)
            return null;
        String text = textAttribute.getValue();

        return new StackExchangeComment(commentId, postId, text);
    }

}
