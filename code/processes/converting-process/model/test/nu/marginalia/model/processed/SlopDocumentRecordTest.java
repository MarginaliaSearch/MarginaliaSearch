package nu.marginalia.model.processed;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class SlopDocumentRecordTest {
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestUtil.clearTempDir(testDir);
    }

    @Test
    public void test() throws IOException {
        ByteBuffer workArea = ByteBuffer.allocate(1024);
        var record = new SlopDocumentRecord("example.com", "https://example.com/foo", 1, "OK", "",
                "test",
                "testtest",
                1,
                "HTML3",
                100,
                0xF00BAAL,
                0.5f,
                0xBEEFL,
                null,
                List.of("test1", "test2"),
                new byte[] { 2, 3},
                List.of(GammaCodedSequence.generate(workArea, 1, 3, 5), GammaCodedSequence.generate(workArea, 2, 4, 6)),
                new byte[] { 'a', 'b' },
                List.of(GammaCodedSequence.generate(workArea, 2, 3, 5), GammaCodedSequence.generate(workArea, 3, 4, 6))
        );

        try (var writer = new SlopDocumentRecord.Writer(testDir, 0)) {
            writer.write(record);
        }

        try (var keywordReader = new SlopDocumentRecord.KeywordsProjectionReader(new SlopTable.Ref<>(testDir, 0))) {
            assertTrue(keywordReader.hasMore());
            var readRecord = keywordReader.next();
            assertFalse(keywordReader.hasMore());

            var expected = new SlopDocumentRecord.KeywordsProjection(
                    record.domain(),
                    record.ordinal(),
                    record.htmlFeatures(),
                    record.documentMetadata(),
                    record.length(),
                    record.words(),
                    record.metas(),
                    record.positions(),
                    record.spanCodes(),
                    record.spans()
            );

            Assertions.assertEquals(expected, readRecord);
        }

        try (var docDataReader = new SlopDocumentRecord.MetadataReader(testDir, 0)) {
            assertTrue(docDataReader.hasMore());
            var readRecord = docDataReader.next();
            assertFalse(docDataReader.hasMore());

            var expected2 = new SlopDocumentRecord.MetadataProjection(
                    record.domain(),
                    record.url(),
                    record.ordinal(),
                    record.title(),
                    record.description(),
                    record.htmlFeatures(),
                    record.htmlStandard(),
                    record.length(),
                    record.hash(),
                    record.quality(),
                    record.pubYear()
            );

            Assertions.assertEquals(expected2, readRecord);
        }
    }
}
