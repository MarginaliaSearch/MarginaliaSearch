package nu.marginalia.converting.processor.logic;

import nu.marginalia.converting.processor.MetaRobotsTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaRobotsTagTest {
    MetaRobotsTag metaRobotsTag = new MetaRobotsTag();
    @Test
    public void testNoTag() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Hello</title></head>
                </html>
                """;

        assertTrue(metaRobotsTag.allowIndexingByMetaTag(Jsoup.parse(html)));
    }

    @Test
    public void testRobotsNoIndexTag() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Hello</title>
                    <meta name="robots" content="noindex" />
                </head>
                </html>
                """;

        assertFalse(metaRobotsTag.allowIndexingByMetaTag(Jsoup.parse(html)));
    }

    @Test
    public void testRobotsNoneTag() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Hello</title>
                    <meta name="robots" content="none" />
                </head>
                </html>
                """;

        assertFalse(metaRobotsTag.allowIndexingByMetaTag(Jsoup.parse(html)));
    }

    @Test
    public void testExplicitlyAllowMarginalia() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Hello</title>
                    <meta name="robots" content="none" />
                    <meta name="marginalia-search" content="all" />
                </head>
                </html>
                """;

        assertTrue(metaRobotsTag.allowIndexingByMetaTag(Jsoup.parse(html)));
    }
}