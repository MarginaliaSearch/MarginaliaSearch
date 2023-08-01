package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class IndexLoadKeywords implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IndexLoadKeywords.class);

    private final LinkedBlockingQueue<InsertTask> insertQueue = new LinkedBlockingQueue<>(32);
    private final LoaderIndexJournalWriter journalWriter;

    private record InsertTask(int urlId, int domainId, DocumentMetadata metadata, DocumentKeywords wordSet) {}

    private final Thread runThread;

    private volatile boolean canceled = false;

    @Inject
    public IndexLoadKeywords(LoaderIndexJournalWriter journalWriter) {
        this.journalWriter = journalWriter;
        runThread = new Thread(this, getClass().getSimpleName());
        runThread.start();
    }

    @SneakyThrows
    public void run() {
        while (!canceled) {
            var data = insertQueue.poll(1, TimeUnit.SECONDS);
            if (data != null) {
                journalWriter.putWords(new EdgeId<>(data.domainId), new EdgeId<>(data.urlId), data.metadata(), data.wordSet);
            }
        }
    }

    public void close() throws Exception {
        if (!canceled) {
            canceled = true;
            runThread.join();
            journalWriter.close();
        }
    }

    public void load(LoaderData loaderData, EdgeUrl url, DocumentMetadata metadata, DocumentKeywords words) throws InterruptedException {
        int domainId = loaderData.getDomainId(url.domain);
        int urlId = loaderData.getUrlId(url);

        if (urlId <= 0 || domainId <= 0) {
            logger.warn("Failed to get IDs for {}  -- d={},u={}", url, domainId, urlId);
            return;
        }

        insertQueue.put(new InsertTask(urlId, domainId, metadata, words));
    }
}
