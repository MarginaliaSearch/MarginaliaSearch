package nu.marginalia.loading;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


@Singleton
public class LoaderIndexJournalWriter {

    private static final Logger logger = LoggerFactory.getLogger(LoaderIndexJournalWriter.class);
    private final Path journalPath;

    private IndexJournalSlopWriter currentWriter = null;
    private long recordsWritten = 0;
    private int page;

    private final Map<String, KeywordHasher> hasherByLanguage = new HashMap<>();

    @Inject
    public LoaderIndexJournalWriter(FileStorageService fileStorageService, LanguageConfiguration languageConfiguration) throws IOException {

        for (LanguageDefinition languageDefinition: languageConfiguration.languages()) {
            hasherByLanguage.put(languageDefinition.isoCode(), languageDefinition.keywordHasher());
        }

        var indexArea = IndexLocations.getIndexConstructionArea(fileStorageService);

        journalPath = IndexJournal.allocateName(indexArea);
        page = IndexJournal.numPages(journalPath);

        switchToNextVersion();

        logger.info("Creating Journal Writer {}", indexArea);
    }

    private void switchToNextVersion() throws IOException {
        if (currentWriter != null) {
            currentWriter.close();
        }

        currentWriter = new IndexJournalSlopWriter(journalPath, page++);
    }

    public void putWords(long header, SlopDocumentRecord.KeywordsProjection data) throws IOException
    {
        KeywordHasher hasher = hasherByLanguage.get(data.languageIsoCode());
        if (null == hasher) return;

        if (++recordsWritten > 200_000) {
            recordsWritten = 0;
            switchToNextVersion();
        }

        currentWriter.put(header, data, hasher);
    }

    public void close() throws IOException {
        currentWriter.close();
    }
}
