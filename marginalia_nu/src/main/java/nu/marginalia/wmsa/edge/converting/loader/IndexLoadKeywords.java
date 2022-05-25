package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class IndexLoadKeywords implements Runnable {
    private final EdgeIndexClient client;
    private static final Logger logger = LoggerFactory.getLogger(IndexLoadKeywords.class);
    private final LinkedBlockingQueue<InsertTask> insertQueue = new LinkedBlockingQueue<>(32);

    private record InsertTask(int urlId, int domainId, EdgePageWordSet wordSet) {}

    private final Thread runThread;
    private volatile boolean canceled = false;

    private static final int index = Integer.getInteger("keyword-index", 1);

    @Inject
    public IndexLoadKeywords(EdgeIndexClient client) {
        this.client = client;
        runThread = new Thread(this, getClass().getSimpleName());
        runThread.start();
    }

    @SneakyThrows
    public void run() {
        while (!canceled) {
            var data = insertQueue.poll(1, TimeUnit.SECONDS);
            if (data != null) {
                client.putWords(Context.internal(), new EdgeId<>(data.domainId), new EdgeId<>(data.urlId), -5., data.wordSet, index).blockingSubscribe();
            }
        }
    }

    public void close() throws InterruptedException {
        canceled = true;
        runThread.join();
    }

    public void load(LoaderData loaderData, EdgeUrl url, DocumentKeywords[] words) throws InterruptedException {
        int domainId = loaderData.getDomainId(url.domain);
        int urlId = loaderData.getUrlId(url);

        if (urlId < 0 || domainId < 0) {
            logger.warn("Failed to get IDs for {}  -- d={},u={}", url, domainId, urlId);
        }

        var ws = new EdgePageWordSet();
        for (var doc : words) {
            ws.append(doc.block(), Arrays.asList(doc.keywords()));
        }

        insertQueue.put(new InsertTask(urlId, domainId, ws));
    }
}
