package nu.marginalia.model.processed;

import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SlopDomainLinkRecordTest {
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void tearDown() {
        TestUtil.clearTempDir(testDir);
    }

    @Test
    public void test() throws IOException {
        var record = new SlopDomainLinkRecord("source", "dest");

        try (var writer = new SlopDomainLinkRecord.Writer(testDir, 0)) {
            writer.write(record);
        }

        try (var reader = new SlopDomainLinkRecord.Reader(testDir, 0)) {
            assertTrue(reader.hasMore());
            var readRecord = reader.next();
            assertFalse(reader.hasMore());

            assertEquals(record, readRecord);
        }
    }
}