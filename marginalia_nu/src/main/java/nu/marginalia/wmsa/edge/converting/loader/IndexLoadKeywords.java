package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexWriterClient;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class IndexLoadKeywords implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IndexLoadKeywords.class);

    private final LinkedBlockingQueue<InsertTask> insertQueue = new LinkedBlockingQueue<>(32);
    private final EdgeIndexWriterClient client;

    private record InsertTask(int urlId, int domainId, DocumentKeywords wordSet) {}

    private final Thread runThread;
    private volatile boolean canceled = false;

    private static final int index = Integer.getInteger("keyword-index", 1);

    @Inject
    public IndexLoadKeywords(EdgeIndexWriterClient client) {
        this.client = client;
        runThread = new Thread(this, getClass().getSimpleName());
        runThread.start();
    }

    @SneakyThrows
    public void run() {
        while (!canceled) {
            var data = insertQueue.poll(1, TimeUnit.SECONDS);
            if (data != null) {
                client.putWords(Context.internal(), new EdgeId<>(data.domainId), new EdgeId<>(data.urlId), data.wordSet, index);
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

        if (urlId <= 0 || domainId <= 0) {
            logger.warn("Failed to get IDs for {}  -- d={},u={}", url, domainId, urlId);
            return;
        }

        for (var ws : words) {
            insertQueue.put(new InsertTask(urlId, domainId, ws));
        }
    }
}
