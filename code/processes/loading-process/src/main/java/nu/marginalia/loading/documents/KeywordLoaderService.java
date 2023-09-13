package nu.marginalia.loading.documents;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.io.processed.DocumentRecordParquetFileReader;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.DocumentRecordKeywordsProjection;

import java.io.IOException;
import java.nio.file.Path;

@Singleton
public class KeywordLoaderService {
    private final LoaderIndexJournalWriter writer;

    @Inject
    public KeywordLoaderService(LoaderIndexJournalWriter writer) {
        this.writer = writer;
    }

    public void loadKeywords(DomainIdRegistry domainIdRegistry,
                             Path processedDataPathBase,
                             int untilBatch) throws IOException {
        var documentFiles = ProcessedDataFileNames.listDocumentFiles(processedDataPathBase, untilBatch);
        for (var file : documentFiles) {
            loadKeywordsFromFile(domainIdRegistry, file);
        }
    }

    private void loadKeywordsFromFile(DomainIdRegistry domainIdRegistry, Path file) throws IOException {
        try (var stream = DocumentRecordParquetFileReader.streamKeywordsProjection(file)) {
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