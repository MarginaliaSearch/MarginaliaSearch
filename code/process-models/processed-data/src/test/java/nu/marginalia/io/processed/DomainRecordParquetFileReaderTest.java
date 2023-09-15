package nu.marginalia.io.processed;

import nu.marginalia.model.processed.DomainRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainRecordParquetFileReaderTest {
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
        var first = new DomainRecord(
                "www.marginalia.nu",
                10,
                3,
                5,
                "'sall good man",
                null,
                "127.0.0.1",
                List.of("a", "b")
        );
        var second = new DomainRecord(
                "memex.marginalia.nu",
                0,
                0,
                0,
                "REDIRECT",
                "www.marginalia.nu",
                "127.0.0.1",
                null
        );

        try (var writer = new DomainRecordParquetFileWriter(parquetFile)) {
            writer.write(first);
            writer.write(second);
        }

        var domainNames = DomainRecordParquetFileReader.getDomainNames(parquetFile);
        assertEquals(List.of("www.marginalia.nu", "memex.marginalia.nu"), domainNames);

        var items = DomainRecordParquetFileReader
                .stream(parquetFile)
                .toList();
        assertEquals(List.of(first, second), items);
    }

}