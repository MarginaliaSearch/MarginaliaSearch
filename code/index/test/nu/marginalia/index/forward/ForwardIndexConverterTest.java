package nu.marginalia.index.forward;

import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForwardIndexConverterTest {

    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    Path workDir;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    Path dataDir;
    private Path docsFileId;
    private Path docsFileData;
    private Path docsSpanData;

    int workSetSize = 512;

    @BeforeEach
    void setUp() throws Exception {

        workDir = Files.createTempDirectory(getClass().getSimpleName());

        dictionaryFile = Files.createTempFile("tmp", ".dict");
        dictionaryFile.toFile().deleteOnExit();

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");

        dataDir = Files.createTempDirectory(getClass().getSimpleName());

        try (var writer = new IndexJournalSlopWriter(IndexJournal.allocateName(workDir, "en"), 0)) {
            for (int i = 1; i < workSetSize; i++) {
                createEntry(writer, i);
            }
        }

        docsFileId = dataDir.resolve("docs-i.dat");
        docsFileData = dataDir.resolve("docs-d.dat");
        docsSpanData = dataDir.resolve("docs-s.dat");
    }

    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(dataDir);
        TestUtil.clearTempDir(workDir);
    }

    long createId(long url, long domain) {
        return UrlIdCodec.encodeId((int) domain, (int) url);
    }

    public void createEntry(IndexJournalSlopWriter writer, int id) throws IOException {
        writer.put(
                createId(id, id/20),
                new SlopDocumentRecord.KeywordsProjection(
                        "",
                        -1,
                        id%3,
                        id%5,
                        15,
                        "en",
                        List.of(),
                        new byte[0],
                        List.of(),
                        new byte[0],
                        List.of()
                ),
                new KeywordHasher.AsciiIsh());


    }

    @Test
    void testForwardIndex() throws IOException {

        new ForwardIndexConverter(new FakeProcessHeartbeat(),
                docsFileId,
                docsFileData,
                docsSpanData,
                IndexJournal.findJournal(workDir, "en").stream().toList(),
                new DomainRankings()).convert();

        var forwardReader = new ForwardIndexReader(docsFileId, docsFileData, docsSpanData);

        for (int i = 36; i < workSetSize; i++) {
            long docId = createId(i, i/20);
            assertEquals(0x00FF000000000000L | (i % 5), forwardReader.getDocMeta(docId));
            assertEquals((i % 3), forwardReader.getHtmlFeatures(docId));
            assertEquals(i/20, UrlIdCodec.getDomainId(docId));
        }
    }
}