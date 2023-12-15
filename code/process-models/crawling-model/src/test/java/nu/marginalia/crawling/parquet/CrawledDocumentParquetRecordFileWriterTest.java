package nu.marginalia.crawling.parquet;

import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;

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
    void testWriteRead() throws IOException {
        var original = new CrawledDocumentParquetRecord("www.marginalia.nu",
                "https://www.marginalia.nu/",
                "127.0.0.1",
                false,
                200,
                Instant.now(),
                "text/html",
                "hello world".getBytes());

        try (var writer = new CrawledDocumentParquetRecordFileWriter(tempFile)) {
            writer.write(original);
        }

        var items = new ArrayList<SerializableCrawlData>();

        try (var stream = new ParquetSerializableCrawlDataStream(tempFile)) {
            while (stream.hasNext()) {
                items.add(stream.next());
            }
        }

        assertEquals(2, items.size());

        var firstItem = items.get(0);
        assertInstanceOf(CrawledDomain.class, firstItem);
        var domain = (CrawledDomain) firstItem;
        assertEquals("www.marginalia.nu", domain.domain);
        assertNull(domain.redirectDomain);
        assertEquals("OK", domain.crawlerStatus);
        assertEquals("", domain.crawlerStatusDesc);
        assertEquals(new ArrayList<>(), domain.doc);
        assertEquals(new ArrayList<>(), domain.cookies);

        var secondItem = items.get(1);
        assertInstanceOf(CrawledDocument.class, secondItem);

        var document = (CrawledDocument) secondItem;
        assertEquals("https://www.marginalia.nu/", document.url);
        assertEquals("text/html", document.contentType);
        assertEquals("hello world", document.documentBody);
        assertEquals(200, document.httpStatus);
        assertEquals("https://www.marginalia.nu/", document.canonicalUrl);
    }


}