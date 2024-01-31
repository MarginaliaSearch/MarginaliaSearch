package nu.marginalia.loading;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.IndexLocations;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.writer.IndexJournalWriterPagingImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;

import static nu.marginalia.index.journal.model.IndexJournalEntryData.MAX_LENGTH;

@Singleton
public class LoaderIndexJournalWriter {

    private final IndexJournalWriter indexWriter;
    private static final Logger logger = LoggerFactory.getLogger(LoaderIndexJournalWriter.class);

    private final MurmurHash3_128 hasher = new MurmurHash3_128();
    private final long[] buffer = new long[MAX_LENGTH * 2];


    @Inject
    public LoaderIndexJournalWriter(FileStorageService fileStorageService) throws IOException, SQLException {
        var indexArea = IndexLocations.getIndexConstructionArea(fileStorageService);

        var existingIndexFiles = IndexJournalFileNames.findJournalFiles(indexArea);
        for (var existingFile : existingIndexFiles) {
            Files.delete(existingFile);
        }

        indexWriter = new IndexJournalWriterPagingImpl(indexArea);
    }

    public void putWords(long combinedId,
                         int features,
                         DocumentMetadata metadata,
                         DocumentKeywords wordSet) {

        putWords(combinedId, features, metadata.encode(), wordSet);
    }

    @SneakyThrows
    public void putWords(long combinedId,
                         int features,
                         long metadata,
                         DocumentKeywords wordSet) {

        if (wordSet.isEmpty()) {
            logger.info("Skipping zero-length word set for {}", combinedId);
            return;
        }

        if (combinedId <= 0) {
            logger.warn("Bad ID: {}", combinedId);
            return;
        }

        var pointer = wordSet.newPointer();

        while (pointer.hasMore()) {
            int i = 0;

            while (i < buffer.length
                && pointer.advancePointer())
            {
                final long hashedKeyword = hasher.hashKeyword(pointer.getKeyword());

                buffer[i++] = hashedKeyword;
                buffer[i++] = pointer.getMetadata();
            }

            var entry = new IndexJournalEntryData(i, buffer);
            var header = new IndexJournalEntryHeader(combinedId, features, metadata);

            indexWriter.put(header, entry);
        }

    }

    public void close() throws Exception {
        indexWriter.close();
    }
}
