package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class IndexLoadKeywords implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IndexLoadKeywords.class);

    private final LinkedBlockingQueue<InsertTask> insertQueue = new LinkedBlockingQueue<>(32);
    private final LoaderIndexJournalWriter journalWriter;

    private record InsertTask(long combinedId,
                              int features,
                              DocumentMetadata metadata,
                              DocumentKeywords wordSet) {}

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
                journalWriter.putWords(data.combinedId,
                        data.features,
                        data.metadata(),
                        data.wordSet);
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

    public void load(LoaderData loaderData,
                     int ordinal,
                     EdgeUrl url,
                     int features,
                     DocumentMetadata metadata,
                     DocumentKeywords words) throws InterruptedException {
        long combinedId = UrlIdCodec.encodeId(loaderData.getTargetDomainId(), ordinal);

        if (combinedId <= 0) {
            logger.warn("Failed to get IDs for {}  -- c={}", url, combinedId);
            return;
        }

        insertQueue.put(new InsertTask(combinedId, features, metadata, words));
    }
}
