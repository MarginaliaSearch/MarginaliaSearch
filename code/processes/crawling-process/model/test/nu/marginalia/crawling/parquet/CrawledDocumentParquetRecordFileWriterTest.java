package nu.marginalia.crawling.parquet;

import nu.marginalia.io.crawldata.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecord;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileWriter;
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
        // Create a record
        var original = new CrawledDocumentParquetRecord("www.marginalia.nu",
                "https://www.marginalia.nu/",
                "127.0.0.1",
                false,
                200,
                Instant.now(),
                "text/html",
                "hello world".getBytes(),
                null,
                null, null);

        // Write the record to a file
        try (var writer = new CrawledDocumentParquetRecordFileWriter(tempFile)) {
            writer.write(original);
        }

        // Read the file back
        var items = new ArrayList<SerializableCrawlData>();
        try (var stream = new ParquetSerializableCrawlDataStream(tempFile)) {
            while (stream.hasNext()) {
                items.add(stream.next());
            }
        }

        // Verify the contents, we should have a domain and a document
        assertEquals(2, items.size());

        // Verify the domain
        var firstItem = items.get(0);
        assertInstanceOf(CrawledDomain.class, firstItem);

        var domain = (CrawledDomain) firstItem;
        assertEquals("www.marginalia.nu", domain.domain);
        assertNull(domain.redirectDomain);
        assertEquals("OK", domain.crawlerStatus);
        assertEquals("", domain.crawlerStatusDesc);
        assertEquals(new ArrayList<>(), domain.doc);
        assertEquals(new ArrayList<>(), domain.cookies);

        // Verify the document
        var secondItem = items.get(1);
        assertInstanceOf(CrawledDocument.class, secondItem);

        var document = (CrawledDocument) secondItem;
        assertEquals("https://www.marginalia.nu/", document.url);
        assertEquals("text/html", document.contentType);
        assertEquals("hello world", document.documentBody());
        assertEquals(200, document.httpStatus);
    }

    // This is an inspection hatch test that reads a file from the odduck.neocities.org domain that didn't load properly,
    // leaving as-is in case we need to look into other files in the future
    @Test
    public void testOdduck() {
        Path testPath = Path.of("/home/vlofgren/Exports/22efad51-oddduck.neocities.org.parquet");

        // Skip if the file doesn't exist
        if (!Files.exists(testPath)) {
            return;
        }

        // Read the file
        try (var stream = new ParquetSerializableCrawlDataStream(testPath)) {
            while (stream.hasNext()) {
                var item = stream.next();
                if (item instanceof CrawledDocument doc) {
                    System.out.println(doc.url);
                    System.out.println(doc.contentType);
                    System.out.println(doc.httpStatus);
                    System.out.println(doc.documentBody().length());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}