package nu.marginalia.wmsa.edge.crawler.domain.processor;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlTagCleanerTest {

    HtmlTagCleaner tagCleaner = new HtmlTagCleaner();

    public String cleanTag(String text) {
        var doc = Jsoup.parse(text);
        tagCleaner.clean(doc);
        return doc.text();
    }

    @Test
    public void testBriefCodeTag() {
        assertEquals("hello", cleanTag("<code>hello</code>"));
        assertEquals("System out println", cleanTag("<code>System.out.println</code>"));
        assertEquals("hello", cleanTag("<code>hello()</code>"));
        assertEquals("hello", cleanTag("<code>&lt;hello&gt;</code>"));
        assertEquals("hello", cleanTag("<code>hello(p,q)</code>"));
        assertEquals("hello", cleanTag("<code>hello(p,q);</code>"));
    }
}