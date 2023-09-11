package nu.marginalia.io.processed;

import nu.marginalia.model.processed.ProcessedDomainData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedDomainParquetFileReaderTest {
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
        var first = new ProcessedDomainData(
                "www.marginalia.nu",
                10,
                3,
                5,
                "'sall good man",
                null,
                "127.0.0.1"
        );
        var second = new ProcessedDomainData(
                "memex.marginalia.nu",
                0,
                0,
                0,
                "REDIRECT",
                "www.marginalia.nu",
                "127.0.0.1"
        );

        try (var writer = new ProcessedDomainParquetFileWriter(parquetFile)) {
            writer.write(first);
            writer.write(second);
        }

        var domainNames = ProcessedDomainParquetFileReader.getDomainNames(parquetFile);
        assertEquals(List.of("www.marginalia.nu", "memex.marginalia.nu"), domainNames);

        var items = ProcessedDomainParquetFileReader
                .stream(parquetFile)
                .toList();
        assertEquals(List.of(first, second), items);
    }

}