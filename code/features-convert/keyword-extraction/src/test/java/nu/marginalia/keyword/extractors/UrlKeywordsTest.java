package nu.marginalia.keyword.extractors;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class UrlKeywordsTest {
    private final PorterStemmer ps = new PorterStemmer();

    @Test
    void containsDomain() throws URISyntaxException {
        var keywords = new UrlKeywords(new EdgeUrl("https://memex.marginalia.nu/log/69-creepy-website-similarity.gmi"));
        assertTrue(keywords.containsDomain(ps.stemWord("memex")));
        assertTrue(keywords.containsDomain(ps.stemWord("marginalia")));
    }

    @Test
    void containsDomainNoWWWNoCom() throws URISyntaxException {
        var keywords = new UrlKeywords(new EdgeUrl("https://www.example.com/log/69-creepy-website-similarity.gmi"));
        assertTrue(keywords.containsDomain(ps.stemWord("example")));
        assertFalse(keywords.containsDomain(ps.stemWord("www")));
        assertFalse(keywords.containsDomain(ps.stemWord("com")));
    }

    @Test
    void pathFragments() throws URISyntaxException {
        var keywords = new UrlKeywords(new EdgeUrl("https://memex.marginalia.nu/log/69-creepy-website-similarity.gmi"));
        assertTrue(keywords.containsUrl(ps.stemWord("creepy")));
        assertTrue(keywords.containsUrl(ps.stemWord("website")));
        assertTrue(keywords.containsUrl(ps.stemWord("similarity")));
        assertTrue(keywords.containsUrl(ps.stemWord("69")));
        assertTrue(keywords.containsUrl(ps.stemWord("log")));
        assertFalse(keywords.containsUrl(ps.stemWord("memex")));
    }
}