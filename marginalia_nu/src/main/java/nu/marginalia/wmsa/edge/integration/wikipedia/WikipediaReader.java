package nu.marginalia.wmsa.edge.integration.wikipedia;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.integration.wikipedia.model.WikipediaArticle;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.util.function.Consumer;

public class WikipediaReader {

    private final Thread runThread;
    private final String zimFile;
    private final EdgeDomain domain;
    private final Consumer<WikipediaArticle> postConsumer;

    public WikipediaReader(String zimFile, EdgeDomain domain, Consumer<WikipediaArticle> postConsumer) {
        this.zimFile = zimFile;
        this.domain = domain;
        this.postConsumer = postConsumer;

        runThread = new Thread(this::run, "WikipediaReader");
        runThread.start();
    }

    @SneakyThrows
    private void run() {
        var zr = new ZIMReader(new ZIMFile(zimFile));

        zr.forEachArticles((originalUrl, art) -> {
            if (art != null) {
                postConsumer.accept(new WikipediaArticle(synthesizeUrl(originalUrl), art));
            }
        }, p -> true);
    }

    private EdgeUrl synthesizeUrl(String originalUrl) {
        return new EdgeUrl("https", domain, null, "/wiki/"+originalUrl, null);
    }

    public void join() throws InterruptedException {
        runThread.join();
    }
}
