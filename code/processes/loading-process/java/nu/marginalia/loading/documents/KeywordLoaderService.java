package nu.marginalia.loading.documents;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.loading.LoaderIndexJournalWriter;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.slop.SlopTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

@Singleton
public class KeywordLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(KeywordLoaderService.class);
    private final LoaderIndexJournalWriter writer;

    @Inject
    public KeywordLoaderService(LoaderIndexJournalWriter writer) {
        this.writer = writer;
    }

    public boolean loadKeywords(DomainIdRegistry domainIdRegistry,
                             ProcessHeartbeat heartbeat,
                             LoaderInputData inputData) throws IOException
    {
        try (var task = heartbeat.createAdHocTaskHeartbeat("KEYWORDS")) {

            Collection<SlopTable.Ref<SlopDocumentRecord>> documentFiles = inputData.listDocumentFiles();
            int processed = 0;

            for (SlopTable.Ref<SlopDocumentRecord> pageRef : task.wrap("LOAD", documentFiles)) {
                try (var keywordsReader = new SlopDocumentRecord.KeywordsProjectionReader(pageRef)) {
                    logger.info("Loading keywords from {}", pageRef);

                    while (keywordsReader.hasMore()) {
                        var projection = keywordsReader.next();

                        long combinedId = UrlIdCodec.encodeId(
                                domainIdRegistry.getDomainId(projection.domain()),
                                projection.ordinal());

                        writer.putWords(combinedId, projection);
                    }
                }
            }
        }
        catch (IOException e) {
            logger.error("Failed to load keywords", e);
            throw e;
        }

        return true;
    }


    public void close() throws IOException {
        writer.close();
    }
}