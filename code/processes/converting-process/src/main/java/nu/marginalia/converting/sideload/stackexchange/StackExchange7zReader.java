package nu.marginalia.converting.sideload.stackexchange;

import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Deprecated
public class StackExchange7zReader {
    private final Path pathTo7zFile;

    public StackExchange7zReader(Path pathTo7zFile) {
        this.pathTo7zFile = pathTo7zFile;
    }

    public List<String> getIds() throws IOException, XMLStreamException {
        try (SevenZFile file = new SevenZFile(pathTo7zFile.toFile())) {
            for (SevenZArchiveEntry entry : file.getEntries()) {
                if ("Posts.xml".equals(entry.getName())) {
                    return getIds(file, entry);
                }
            }
        }
        return List.of();
    }


    private List<String> getIds(SevenZFile file, SevenZArchiveEntry entry) throws IOException, XMLStreamException {
        List<String> ids = new ArrayList<>(10000);

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        var idField = new QName("Id");

        try (var inputStream = file.getInputStream(entry)) {

            var xmlReader = xmlInputFactory.createXMLEventReader(inputStream);

            while (xmlReader.hasNext()) {
                var event = xmlReader.nextEvent();
                if (!event.isStartElement()) continue;

                var startEvent = event.asStartElement();
                if (!"row".equals(startEvent.getName().getLocalPart())) continue;

                var fieldValue = startEvent.getAttributeByName(idField);
                if (fieldValue != null) {
                    ids.add(fieldValue.getValue());
                }
            }
        }

        return ids;
    }

    public Iterator<Post> postIterator() throws IOException, XMLStreamException {
        SevenZFile postsFile = new SevenZFile(pathTo7zFile.toFile());
        SevenZFile commentsFile = new SevenZFile(pathTo7zFile.toFile());

        SevenZArchiveEntry postsEntry = null;
        SevenZArchiveEntry commentsEntry = null;

        for (SevenZArchiveEntry entry : postsFile.getEntries()) {
            if ("Posts.xml".equals(entry.getName())) {
                postsEntry = entry;
                break;
            }
        }

        for (SevenZArchiveEntry entry : commentsFile.getEntries()) {
            if ("Comments.xml".equals(entry.getName())) {
                commentsEntry = entry;
                break;
            }
        }

        if (postsEntry == null || commentsEntry == null) {
            postsFile.close();
            commentsFile.close();

            throw new IOException("Posts.xml or Comments.xml not found in 7z file");
        }

        var postsInputStream = postsFile.getInputStream(postsEntry);
        var commentsInputStream = commentsFile.getInputStream(commentsEntry);

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        var postsXmlReader = xmlInputFactory.createXMLEventReader(postsInputStream);
        var commentsXmlReader = xmlInputFactory.createXMLEventReader(commentsInputStream);

        QName titleName = new QName("Title");
        QName idName = new QName("Id");
        QName bodyName = new QName("Body");
        QName tagsName = new QName("Tags");
        QName creationDateName = new QName("CreationDate");
        QName score = new QName("Score");

        QName postIdName = new QName("PostId");
        QName textName = new QName("Text");

        return new Iterator<>() {
            Post next = null;
            Comment nextComment = null;

            @SneakyThrows
            @Override
            public boolean hasNext() {
                if (next != null)
                    return true;

                while (postsXmlReader.hasNext()) {
                    var event = postsXmlReader.nextEvent();
                    if (!event.isStartElement()) continue;

                    var startEvent = event.asStartElement();
                    if (!"row".equals(startEvent.getName().getLocalPart())) continue;

                    var scoreAttribute = startEvent.getAttributeByName(score);
                    if (scoreAttribute == null) continue;
                    int score = Integer.parseInt(scoreAttribute.getValue());
                    if (score < 1) continue;

                    var titleAttribute = startEvent.getAttributeByName(titleName);
                    if (titleAttribute == null) continue;
                    String title = titleAttribute.getValue();

                    var idAttribute = startEvent.getAttributeByName(idName);
                    if (idAttribute == null) continue;
                    int id = Integer.parseInt(idAttribute.getValue());

                    var bodyAttribute = startEvent.getAttributeByName(bodyName);
                    if (bodyAttribute == null) continue;
                    String body = bodyAttribute.getValue();

                    var tagsAttribute = startEvent.getAttributeByName(tagsName);
                    if (tagsAttribute == null) continue;
                    String tags = tagsAttribute.getValue();
                    List<String> tagsParsed = parseTags(tags);
                    var creationDateAttribute = startEvent.getAttributeByName(creationDateName);
                    if (creationDateAttribute == null) continue;
                    String creationDate = creationDateAttribute.getValue();
                    int year = Integer.parseInt(creationDate.substring(0, 4));

                    List<Comment> comments = new ArrayList<>();
                    do {
                        if (nextComment == null) continue;

                        if (nextComment.postId > id) {
                            break;
                        }
                        if (nextComment.postId == id) {
                            comments.add(nextComment);
                            nextComment = null;
                        }
                    }
                    while (readNextComment());

                    next = new Post(title, tagsParsed, year, id, body, comments);
                    return true;
                }

                postsInputStream.close();
                commentsInputStream.close();
                postsFile.close();
                commentsFile.close();

                return false;
            }

            private boolean readNextComment() throws XMLStreamException {
                while (commentsXmlReader.hasNext()) {
                    var event = commentsXmlReader.nextEvent();
                    if (!event.isStartElement()) continue;

                    var startEvent = event.asStartElement();
                    if (!"row".equals(startEvent.getName().getLocalPart())) continue;

                    var postIdAttribute = startEvent.getAttributeByName(postIdName);
                    if (postIdAttribute == null) continue;
                    int postId = Integer.parseInt(postIdAttribute.getValue());

                    var textAttribute = startEvent.getAttributeByName(textName);
                    if (textAttribute == null) continue;
                    String text = textAttribute.getValue();

                    nextComment = new Comment(postId, text);
                    return true;
                }
                return false;
            }

            @Override
            public Post next() {
                if (hasNext()) {
                    var ret = next;
                    next = null;
                    return ret;
                }

                throw new IllegalStateException("No more posts");
            }
        };
    }

    private List<String> parseTags(String tags) {
        return Arrays.stream(tags.split("<|>"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }


    public record Post(String title, List<String> tags, int year, int id, String body, List<Comment> comments) {

    }

    public record Comment(int postId, String text) {

    }
}
