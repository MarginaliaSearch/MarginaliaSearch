package nu.marginalia.loading.documents;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.io.processed.DocumentRecordParquetFileReader;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.loading.LoaderIndexJournalWriter;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.DocumentRecordKeywordsProjection;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

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
                             LoaderInputData inputData) throws IOException {
        try (var task = heartbeat.createAdHocTaskHeartbeat("KEYWORDS")) {

            var documentFiles = inputData.listDocumentFiles();
            int processed = 0;

            for (var file : documentFiles) {
                task.progress("LOAD", processed++, documentFiles.size());

                loadKeywordsFromFile(domainIdRegistry, file);
            }

            task.progress("LOAD", processed, documentFiles.size());
        }

        logger.info("Finished");

        return true;
    }

    private void loadKeywordsFromFile(DomainIdRegistry domainIdRegistry, Path file) throws IOException {
        try (var stream = DocumentRecordParquetFileReader.streamKeywordsProjection(file)) {
            logger.info("Loading keywords from {}", file);

            stream.filter(DocumentRecordKeywordsProjection::hasKeywords)
                    .forEach(proj -> insertKeywords(domainIdRegistry, proj));
        }
    }

    private void insertKeywords(DomainIdRegistry domainIdRegistry,
                                DocumentRecordKeywordsProjection projection)
    {
        long combinedId = UrlIdCodec.encodeId(
                domainIdRegistry.getDomainId(projection.domain),
                projection.ordinal);

        var words = new DocumentKeywords(
                projection.words.toArray(String[]::new),
                projection.metas.toArray()
        );

        writer.putWords(combinedId,
                projection.htmlFeatures,
                projection.documentMetadata,
                words);
    }
}