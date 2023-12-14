package nu.marginalia.crawling.parquet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.net.WarcRecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrawledDocumentParquetRecordFileWriterTest {
    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile("test", ".parquet");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    void write() throws IOException {
        var original = new CrawledDocumentParquetRecord("www.marginalia.nu",
                "https://www.marginalia.nu/",
                "127.0.0.1",
                false,
                200,
                "text/html",
                "hello world".getBytes());

        try (var writer = new CrawledDocumentParquetRecordFileWriter(tempFile)) {
            writer.write(original);
        }

        try (var stream = CrawledDocumentParquetRecordFileReader.stream(tempFile)) {
            var actual = stream.findFirst().orElseThrow();
            assertEquals(original, actual);
        }
    }

}