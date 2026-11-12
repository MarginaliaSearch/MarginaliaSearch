package nu.marginalia.rss.svc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleFeedParserTest {
    @Test
    void emptyFeeds() {
        for (String xml : List.of("<rss><channel/></rss>", "<feed/>", "<atom:feed/>", "<rdf:RDF/>")) {
            assertTrue(SimpleFeedParser.parse(xml).isEmpty(), xml);
        }
    }

    @Test
    void invalidDocuments() {
        for (String xml : List.of("", "Unavailable", "<html><body>Error</body></html>")) {
            assertThrows(IllegalArgumentException.class, () -> SimpleFeedParser.parse(xml), xml);
        }
    }
}
