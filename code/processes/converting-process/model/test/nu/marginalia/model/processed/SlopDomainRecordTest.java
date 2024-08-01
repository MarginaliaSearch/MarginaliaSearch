package nu.marginalia.model.processed;

import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlopDomainRecordTest {

    private Path testDir;

    @BeforeEach
    void setUp() throws IOException  {
        testDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void tearDown() throws IOException {
        TestUtil.clearTempDir(testDir);
    }

    @Test
    public void testWriteRead() throws IOException {
        var record = new SlopDomainRecord(
                "domain",
                1, 2, 3,
                "state",
                "redirectDomain",
                "192.168.0.1",
                List.of("rss1", "rss2")
        );

        try (var writer = new SlopDomainRecord.Writer(testDir, 0)) {
            writer.write(record);
        }

        try (var reader = new SlopDomainRecord.Reader(testDir, 0)) {
            assertTrue(reader.hasMore());
            var readRecord = reader.next();
            assertFalse(reader.hasMore());

            Assertions.assertEquals(record, readRecord);
        }

        try (var dwrReader  = new SlopDomainRecord.DomainWithIpReader(testDir, 0)) {
            assertTrue(dwrReader.hasMore());
            var readRecord = dwrReader.next();
            assertFalse(dwrReader.hasMore());

            Assertions.assertEquals(new SlopDomainRecord.DomainWithIpProjection("domain", "192.168.0.1"), readRecord);
        }

        try (var dnReader = new SlopDomainRecord.DomainNameReader(testDir, 0)) {
            assertTrue(dnReader.hasMore());
            var readRecord = dnReader.next();
            assertFalse(dnReader.hasMore());

            Assertions.assertEquals("domain", readRecord);
        }
    }
}