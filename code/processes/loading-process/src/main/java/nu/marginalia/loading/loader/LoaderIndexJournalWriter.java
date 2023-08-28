package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.writer.IndexJournalWriterPagingImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;

import static nu.marginalia.index.journal.model.IndexJournalEntryData.MAX_LENGTH;

@Singleton
public class LoaderIndexJournalWriter {

    private final IndexJournalWriter indexWriter;
    private static final Logger logger = LoggerFactory.getLogger(LoaderIndexJournalWriter.class);

    @Inject
    public LoaderIndexJournalWriter(FileStorageService fileStorageService) throws IOException, SQLException {
        var indexArea = fileStorageService.getStorageByType(FileStorageType.INDEX_STAGING);

        var existingIndexFiles = IndexJournalFileNames.findJournalFiles(indexArea.asPath());
        for (var existingFile : existingIndexFiles) {
            Files.delete(existingFile);
        }

        indexWriter = new IndexJournalWriterPagingImpl(indexArea.asPath());
    }

    MurmurHash3_128 hasher = new MurmurHash3_128();

    @SneakyThrows
    public void putWords(long combinedId,
                         int features,
                         DocumentMetadata metadata,
                         DocumentKeywords wordSet) {
        if (wordSet.keywords().length == 0) {
            logger.info("Skipping zero-length word set for {}", combinedId);
            return;
        }

        if (combinedId <= 0) {
            logger.warn("Bad ID: {}", combinedId);
            return;
        }

        String[] words = wordSet.keywords();
        long[] wordIds = new long[wordSet.size()];
        long[] meta = wordSet.metadata();

        Arrays.parallelSetAll(wordIds, i -> hasher.hashNearlyASCII(words[i]));

        long[] buffer = new long[MAX_LENGTH * 2];
        for (int start = 0; start < words.length; ) {
            int end = Math.min(start + MAX_LENGTH, words.length);

            for (int i = 0; i < end - start; i++) {
                buffer[2*i] = wordIds[i];
                buffer[2*i + 1] = meta[i];
            }

            var entry = new IndexJournalEntryData(end-start, buffer);
            var header = new IndexJournalEntryHeader(combinedId, features, metadata.encode());

            indexWriter.put(header, entry);

            start = end;
        }

    }

    public void close() throws Exception {
        indexWriter.close();
    }
}
