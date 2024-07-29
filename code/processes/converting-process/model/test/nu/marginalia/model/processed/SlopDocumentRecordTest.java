package nu.marginalia.model.processed;

import nu.marginalia.sequence.GammaCodedSequence;
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
        var recordShort = new SlopDocumentRecord("test", "https://test/foo", 0, "ERROR", "Cosmic Ray");
        var recordLong = new SlopDocumentRecord("example.com", "https://example.com/foo", 1, "OK", "",
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
            writer.write(recordShort);
            writer.write(recordLong);
        }

        try (var keywordReader = new SlopDocumentRecord.KeywordsProjectionReader(testDir, 0)) {
            assertTrue(keywordReader.hasMore());
            var readRecord = keywordReader.next();
            assertFalse(keywordReader.hasMore());

            var expected = new SlopDocumentRecord.KeywordsProjection(
                    recordLong.domain(),
                    recordLong.ordinal(),
                    recordLong.htmlFeatures(),
                    recordLong.documentMetadata(),
                    recordLong.length(),
                    recordLong.words(),
                    recordLong.metas(),
                    recordLong.positions(),
                    recordLong.spanCodes(),
                    recordLong.spans()
            );

            Assertions.assertEquals(expected, readRecord);
        }

        try (var docDataReader = new SlopDocumentRecord.MetadataReader(testDir, 0)) {
            assertTrue(docDataReader.hasMore());
            var readRecord = docDataReader.next();
            assertFalse(docDataReader.hasMore());

            var expected2 = new SlopDocumentRecord.MetadataProjection(
                    recordLong.domain(),
                    recordLong.url(),
                    recordLong.ordinal(),
                    recordLong.title(),
                    recordLong.description(),
                    recordLong.htmlFeatures(),
                    recordLong.htmlStandard(),
                    recordLong.length(),
                    recordLong.hash(),
                    recordLong.quality(),
                    recordLong.pubYear()
            );

            Assertions.assertEquals(expected2, readRecord);
        }
    }
}
