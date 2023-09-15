package nu.marginalia.io.processed;

import nu.marginalia.model.processed.DomainLinkRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainLinkRecordParquetFileReaderTest {
    Path parquetFile;

    @BeforeEach
    public void setUp() throws IOException {
        parquetFile = Files.createTempFile(getClass().getSimpleName(), ".parquet");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(parquetFile);
    }

    @Test
    public void testReadFull() throws IOException {
        var first = new DomainLinkRecord(
                "www.marginalia.nu",
                "memex.marginalia.nu");
        var second = new DomainLinkRecord(
                "memex.marginalia.nu",
                "search.marginalia.nu"
        );

        try (var writer = new DomainLinkRecordParquetFileWriter(parquetFile)) {
            writer.write(first);
            writer.write(second);
        }

        var items = DomainLinkRecordParquetFileReader
                .stream(parquetFile)
                .toList();
        assertEquals(List.of(first, second), items);
    }

}