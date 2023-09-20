package nu.marginalia.integration.stackexchange.xml;

import nu.marginalia.integration.stackexchange.model.StackExchangePost;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StackExchangeXmlPostReader {
    private final Path pathTo7zFile;

    public StackExchangeXmlPostReader(Path pathTo7zFile) {
        this.pathTo7zFile = pathTo7zFile;
    }

    public Iterator<StackExchangePost> iterator() throws IOException, XMLStreamException {
        return iterator(new StackExchange7zXmlEventReaderSource(pathTo7zFile, "Posts.xml"));
    }

    static Iterator<StackExchangePost> iterator(XmlEventReaderSource source) {
        return new StackExchangeXmlIterator<>(source, StackExchangeXmlPostReader::parseEvent);
    }

    private static final QName titleName = new QName("Title");
    private static final QName idName = new QName("Id");
    private static final QName bodyName = new QName("Body");
    private static final QName tagsName = new QName("Tags");
    private static final QName creationDateName = new QName("CreationDate");
    private static final QName score = new QName("Score");
    private static final QName parentId = new QName("ParentId");
    private static final QName postTypeId = new QName("PostTypeId");

    private static StackExchangePost parseEvent(XMLEvent event) {
        var startEvent = event.asStartElement();
        if (!"row".equals(startEvent.getName().getLocalPart()))
            return null;

        var scoreAttribute = startEvent.getAttributeByName(score);
        if (scoreAttribute == null)
            return null;
        int score = Integer.parseInt(scoreAttribute.getValue());
        if (score < 1)
            return null;

        var titleAttribute = startEvent.getAttributeByName(titleName);
        String title = null;
        if (titleAttribute != null)
            title = titleAttribute.getValue();

        var idAttribute = startEvent.getAttributeByName(idName);
        if (idAttribute == null)
            return null;
        int id = Integer.parseInt(idAttribute.getValue());

        var parentIdAttribute = startEvent.getAttributeByName(parentId);
        Integer parentId = null;
        if (parentIdAttribute != null)
            parentId = Integer.parseInt(parentIdAttribute.getValue());

        var postTypeIdAttribute = startEvent.getAttributeByName(postTypeId);
        if (postTypeIdAttribute == null)
            return null;
        int postTypeId = Integer.parseInt(postTypeIdAttribute.getValue());

        var bodyAttribute = startEvent.getAttributeByName(bodyName);
        if (bodyAttribute == null)
            return null;
        String body = bodyAttribute.getValue();

        var tagsAttribute = startEvent.getAttributeByName(tagsName);
        List<String> tagsParsed;
        if (tagsAttribute == null) {
            tagsParsed = List.of();
        }
        else {
            String tags = tagsAttribute.getValue();
            tagsParsed = parseTags(tags);
        }

        var creationDateAttribute = startEvent.getAttributeByName(creationDateName);
        if (creationDateAttribute == null)
            return null;
        String creationDate = creationDateAttribute.getValue();
        int year = Integer.parseInt(creationDate.substring(0, 4));

        return new StackExchangePost(title, tagsParsed, year, id, parentId, postTypeId, body);
    }

    private static final Pattern splitPattern = Pattern.compile("[<>]+");
    private static List<String> parseTags(String tags) {
        return Arrays.stream(splitPattern.split(tags))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
