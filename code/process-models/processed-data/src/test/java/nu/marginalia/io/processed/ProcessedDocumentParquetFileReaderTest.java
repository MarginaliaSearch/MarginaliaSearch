package nu.marginalia.io.processed;

import nu.marginalia.model.processed.ProcessedDocumentData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedDocumentParquetFileReaderTest {
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
    public void test() throws IOException {
        var doc = new ProcessedDocumentData(
                "www.marginalia.nu",
                "https://www.marginalia.nu/",
                0,
                "OK",
                null,
                "Itsa me, Marginalia!",
                "Hello World",
                3,
                "HTML5",
                123,
                0xF00BA3L,
                0.25f,
                null,
                List.of("Hello", "world"),
                List.of(2L, 3L)
        );

        try (var writer = new ProcessedDocumentParquetFileWriter(parquetFile)) {
            writer.write(doc);
        }

        var read = ProcessedDocumentParquetFileReader.stream(parquetFile).toList();
        assertEquals(List.of(doc), read);
    }

    @Test
    public void testHugePayload() throws IOException {
        List<String> words = IntStream.range(0, 100000).mapToObj(Integer::toString).toList();
        List<Long> metas = LongStream.range(0, 100000).boxed().toList();

        var doc = new ProcessedDocumentData(
                "www.marginalia.nu",
                "https://www.marginalia.nu/",
                0,
                "OK",
                null,
                "Itsa me, Marginalia!",
                "Hello World",
                3,
                "HTML5",
                123,
                0xF00BA3L,
                0.25f,
                null,
                words,
                metas
        );

        try (var writer = new ProcessedDocumentParquetFileWriter(parquetFile)) {
            writer.write(doc);
        }

        var read = ProcessedDocumentParquetFileReader.stream(parquetFile).toList();
        assertEquals(List.of(doc), read);
    }

}