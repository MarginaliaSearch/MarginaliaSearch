package nu.marginalia.loading.loader;

import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleFile;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.loading.LoaderIndexJournalWriter;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class LoaderIndexJournalWriterTest {

    Path tempDir;
    LoaderIndexJournalWriter writer;
    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
        FileStorageService storageService = Mockito.mock(FileStorageService.class);
        Mockito.when(storageService.getStorageByType(FileStorageType.INDEX_STAGING)).
                thenReturn(new FileStorage(null, null, null, null, tempDir.toString(),
                        "test"));
        writer = new LoaderIndexJournalWriter(storageService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        writer.close();
        List<Path> junk = Files.list(tempDir).toList();
        for (var item : junk)
            Files.delete(item);
        Files.delete(tempDir);
    }

    @Test
    public void testBreakup() throws Exception {
        String[] keywords = new String[2000];
        long[] metadata = new long[2000];
        for (int i = 0; i < 2000; i++) {
            keywords[i] = Integer.toString(i);
            metadata[i] = i+1;
        }
        DocumentKeywords words = new DocumentKeywords(keywords, metadata);
        writer.putWords(1, 0, new DocumentMetadata(0),
                words);

        writer.close();

        List<Path> journalFiles =IndexJournalFileNames.findJournalFiles(tempDir);
        assertEquals(1, journalFiles.size());

        var reader = new IndexJournalReaderSingleFile(journalFiles.get(0));
        List<Long> docIds = new ArrayList<>();
        reader.forEachDocId(docIds::add);
        assertEquals(List.of(1L, 1L), docIds);

        List<Long> metas = new ArrayList<Long>();
        var ptr = reader.newPointer();
        while (ptr.nextDocument()) {
            while (ptr.nextRecord()) {
                metas.add(ptr.wordMeta());
            }
        }

        assertEquals(LongStream.of(metadata).boxed().toList(), metas);
    }
}