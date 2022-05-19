package nu.marginalia.wmsa.edge.integration.stackoverflow;

import gnu.trove.map.hash.TIntObjectHashMap;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowQuestionData;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;

public class StackOverflowPostsReader extends DefaultHandler {
    private static final int MAX_QUESTION_WINDOW_SIZE = 10_000;

    private final Thread runThread;
    private final String postsFile;
    private final EdgeDomain domain;
    private final Consumer<StackOverflowPost> postConsumer;

    private Deque<StackOverflowQuestionData> questionWindow = new LinkedList<>();
    private final TIntObjectHashMap<StackOverflowQuestionData> questionsById = new TIntObjectHashMap<>(1_000_000);

    public StackOverflowPostsReader(String postsFile, EdgeDomain domain, Consumer<StackOverflowPost> postConsumer) {
        this.postsFile = postsFile;
        this.domain = domain;
        this.postConsumer = postConsumer;
        runThread = new Thread(this::run, "StackOverflowPostReader");
        runThread.start();

    }

    @Override
    public void startElement(String uri, String lName, String qName, Attributes attr) throws SAXException {
        if (!"row".equals(qName)) {
            return;
        }

        if ("1".equals(attr.getValue("PostTypeId"))) {
            onQuestion(attr);
        }
        if ("2".equals(attr.getValue("PostTypeId"))) {
            onReply(attr);
        }
        
        while (questionWindow.size() > MAX_QUESTION_WINDOW_SIZE) {
            var data = questionWindow.removeFirst();
            finalizeQuestion(data);
        }

    }

    private void finalizeQuestion(StackOverflowQuestionData data) {
        questionsById.remove(data.getId());
        var post = createPost(data);
        postConsumer.accept(post);
    }

    private StackOverflowPost createPost(StackOverflowQuestionData data) {
        EdgeUrl url = new EdgeUrl("https", domain, null, "/questions/"+data.getId());

        StringBuilder body = new StringBuilder();
        body.append(data.getQuestion());
        data.getReplies().forEach(body::append);

        return new StackOverflowPost(url, data.getTitle(), body.toString(), data.getQuestion());
    }


    private void onQuestion(Attributes attr) {
        String id = attr.getValue("Id");
        String title = attr.getValue("Title");
        String body = attr.getValue("Body");
        String score = attr.getValue("Score");
        if (parseInt(score) < 0)
            return;

        var data = new StackOverflowQuestionData(parseInt(id), title, body, new ArrayList<>());
        questionsById.put(data.getId(), data);
        questionWindow.addLast(data);
    }

    private void onReply(Attributes attr) {
        String parentId = attr.getValue("ParentId");
        String body = attr.getValue("Body");
        String score = attr.getValue("Score");
        if (parseInt(score) < 0)
            return;

        var data = questionsById.get(parseInt(parentId));
        if (data != null) {
            data.getReplies().add(body);
        }
    }

    private int parseInt(String id) {
        return Integer.parseInt(id);
    }

    @SneakyThrows
    private void run() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        saxParser.parse(postsFile, this);

        while (!questionWindow.isEmpty()) {
            var data = questionWindow.removeFirst();
            finalizeQuestion(data);
        }
    }

    public void join() throws InterruptedException {
        runThread.join();
    }
}
