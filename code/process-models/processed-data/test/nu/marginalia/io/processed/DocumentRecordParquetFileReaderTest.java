package nu.marginalia.io.processed;

import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.model.processed.DocumentRecord;
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

class DocumentRecordParquetFileReaderTest {
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
        var doc = new DocumentRecord(
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
                4L,
                null,
                List.of("Hello", "world"),
                new TLongArrayList(new long[] { 2, 3})
        );

        try (var writer = new DocumentRecordParquetFileWriter(parquetFile)) {
            writer.write(doc);
        }

        var read = DocumentRecordParquetFileReader.stream(parquetFile).toList();
        assertEquals(List.of(doc), read);
    }

    @Test
    public void testHugePayload() throws IOException {
        List<String> words = IntStream.range(0, 100000).mapToObj(Integer::toString).toList();
        TLongArrayList metas = new TLongArrayList(LongStream.range(0, 100000).toArray());

        var doc = new DocumentRecord(
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
                5L,
                null,
                words,
                metas
        );

        try (var writer = new DocumentRecordParquetFileWriter(parquetFile)) {
            writer.write(doc);
        }

        var read = DocumentRecordParquetFileReader.stream(parquetFile).toList();
        assertEquals(List.of(doc), read);
    }

}