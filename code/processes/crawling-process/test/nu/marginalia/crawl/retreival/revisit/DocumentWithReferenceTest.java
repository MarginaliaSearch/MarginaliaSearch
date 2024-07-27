package nu.marginalia.crawl.retreival.revisit;

import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.model.crawldata.CrawledDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentWithReferenceTest {

    // test case for when doc is null
    @Test
    public void getContentTags_docIsNull() {
        // set up test data
        CrawledDocument doc = null;
        CrawlDataReference reference = new CrawlDataReference();

        DocumentWithReference documentWithReference = new DocumentWithReference(doc, reference);

        // execute method under test
        ContentTags contentTags = documentWithReference.getContentTags();

        // verify that returned content tags is empty
        assertTrue(contentTags.isEmpty());
    }

    // test case for when doc is not null, and lastModified and eTag are null
    @Test
    public void getContentTags_lastModifiedAndETagIsNull() {
        // set up test data
        CrawledDocument doc = CrawledDocument.builder().build(); // both lastModified and eTag are null
        CrawlDataReference reference = new CrawlDataReference();

        DocumentWithReference documentWithReference = new DocumentWithReference(doc, reference);

        // execute method under test
        ContentTags contentTags = documentWithReference.getContentTags();

        // verify that returned content tags is empty
        assertTrue(contentTags.isEmpty());
    }

    // test case for when doc is not null, and lastModified and eTag are not null
    @Test
    public void getContentTags_lastModifiedAndETagAreNotNull_NewCrawlData() {
        // set up test data
        CrawledDocument doc = CrawledDocument.builder()
                .etagMaybe("12345")
                .lastModifiedMaybe("67890")
                .documentBody("Test")
                .httpStatus(200)
                .build(); // assume lastModified and eTag are not null
        CrawlDataReference reference = new CrawlDataReference();

        DocumentWithReference documentWithReference = new DocumentWithReference(doc, reference);

        // execute method under test
        ContentTags contentTags = documentWithReference.getContentTags();

        // verify that returned content tags is present
        assertFalse(contentTags.isEmpty());
        assertEquals("12345", contentTags.etag());
        assertEquals("67890", contentTags.lastMod());
    }

    @Test
    public void getContentTags_lastModifiedAndETagAreNotNull_LegacyCrawlData() {
        // set up test data
        CrawledDocument doc = CrawledDocument.builder()
                .headers("""
                        Etag: 12345
                        Last-Modified: 67890
                        """)
                .documentBody("Test")
                .httpStatus(200)
                .build(); // assume lastModified and eTag are not null
        CrawlDataReference reference = new CrawlDataReference();

        DocumentWithReference documentWithReference = new DocumentWithReference(doc, reference);

        // execute method under test
        ContentTags contentTags = documentWithReference.getContentTags();

        // verify that returned content tags is present
        assertFalse(contentTags.isEmpty());
        assertEquals("12345", contentTags.etag());
        assertEquals("67890", contentTags.lastMod());
    }
}