package nu.marginalia.crawling.model;

import nu.marginalia.model.crawldata.CrawledDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrawledDocumentTest {

    /** These tests are AI-generated hence have kinda inconsistent naming */

    @Test
    void getEtagShouldReturnEtagIfPresent() {
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .etagMaybe("12345")
                .build();

        // Etag is present, method should return it.
        String etag = crawledDocument.getEtag();
        assertEquals("12345", etag);
    }

    @Test
    void getEtagShouldReturnNullIfEtagIsAbsentAndHeadersAreNull() {
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .etagMaybe(null)
                .headers(null)
                .build();

        // Etag and headers are absent, method should return null.
        String etag = crawledDocument.getEtag();
        assertNull(etag);
    }

    @Test
    void getEtagShouldReturnNullIfEtagIsAbsentAndHeadersDoNotContainEtag() {
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .etagMaybe(null)
                .headers("Some irrelevant headers")
                .build();

        // Headers do not contain an ETag, method should return null.
        String etag = crawledDocument.getEtag();
        assertNull(etag);
    }

    @Test
    void getEtagShouldReturnEtagFromHeadersIfPresent() {
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .etagMaybe(null)
                .headers("ETag: 67890")
                .build();

        // Headers contain an ETag, method should return it.
        String etag = crawledDocument.getEtag();
        assertEquals("67890", etag);
    }

    @Test
    public void testGetLastModified_withLastModifiedDateInHeaders() {
        // Arrange
        String lastModifiedDate = "Wed, 21 Oct 2015 07:28:00 GMT";
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .headers("Last-Modified: " + lastModifiedDate)
                .build();

        // Act
        String actualLastModifiedDate = crawledDocument.getLastModified();

        // Assert
        assertEquals(lastModifiedDate, actualLastModifiedDate);
    }

    @Test
    public void testGetLastModified_withoutLastModifiedDateInHeaders() {
        // Arrange
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .headers("Some-Other-Header: Some value")
                .build();

        // Act
        String actualLastModifiedDate = crawledDocument.getLastModified();

        // Assert
        assertNull(actualLastModifiedDate);
    }

    @Test
    public void testGetLastModified_withLastModifiedDateInField() {
        // Arrange
        String lastModifiedDate = "Wed, 21 Oct 2015 07:28:00 GMT";
        CrawledDocument crawledDocument = CrawledDocument.builder()
                .lastModifiedMaybe(lastModifiedDate)
                .build();

        // Act
        String actualLastModifiedDate = crawledDocument.getLastModified();

        // Assert
        assertEquals(lastModifiedDate, actualLastModifiedDate);
    }
}