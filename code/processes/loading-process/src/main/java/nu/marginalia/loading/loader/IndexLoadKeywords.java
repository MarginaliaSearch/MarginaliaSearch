package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexLoadKeywords {
    private static final Logger logger = LoggerFactory.getLogger(IndexLoadKeywords.class);
    private final LoaderIndexJournalWriter journalWriter;

    private volatile boolean canceled = false;

    @Inject
    public IndexLoadKeywords(LoaderIndexJournalWriter journalWriter) {
        this.journalWriter = journalWriter;
    }


    public void close() throws Exception {
        if (!canceled) {
            journalWriter.close();
        }
    }

    public void load(LoaderData loaderData,
                     int ordinal,
                     EdgeUrl url,
                     int features,
                     DocumentMetadata metadata,
                     DocumentKeywords words) {
        long combinedId = UrlIdCodec.encodeId(loaderData.getTargetDomainId(), ordinal);

        if (combinedId <= 0) {
            logger.warn("Failed to get IDs for {}  -- c={}", url, combinedId);
            return;
        }

        journalWriter.putWords(combinedId,
                features,
                metadata,
                words);
    }
}
