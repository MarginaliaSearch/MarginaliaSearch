package nu.marginalia.converting.model;

import nu.marginalia.dom.MeasureLengthVisitor;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DocumentTagsTest {

    private static final String html = """
            <html>
            <head>
                <base href="https://www.example.com/subdir/">
                <meta name="generator" content="test">
                <script src="foo.js"></script>
            </head>
            <body>
                <h1>Heading</h1>
                <h2>Subheading</h2>
                <a href="foo.html">link</a>
                <a href="bar.html">link</a>
                <time datetime="2024-01-01">then</time>
                <noscript>text</noscript>
                <iframe src="frame.html"></iframe>
                <video src="clip.mp4"></video>
                <p>Some paragraph text</p>
            </body>
            </html>
            """;

    @Test
    void collectsSameElementsAsDocumentQueries() {
        var doc = Jsoup.parse(html);
        var tags = new DocumentTags(doc);

        Assertions.assertEquals(doc.getElementsByTag("script"), tags.scriptTags());
        Assertions.assertEquals(doc.getElementsByTag("a"), tags.aTags());
        Assertions.assertEquals(doc.getElementsByTag("meta"), tags.metaTags());
        Assertions.assertEquals(doc.getElementsByTag("noscript"), tags.noscriptTags());
        Assertions.assertEquals(doc.getElementsByTag("iframe"), tags.frameTags());
        Assertions.assertEquals(doc.getElementsByTag("time"), tags.timeTags());
        Assertions.assertEquals(doc.getElementsByTag("base"), tags.baseTags());

        Assertions.assertEquals(2, tags.allHeadingTags().size());
        Assertions.assertFalse(tags.hasDateTag());
        Assertions.assertTrue(tags.hasMediaTag());
    }

    @Test
    void measuresSameLengthAsMeasureLengthVisitor() {
        var doc = Jsoup.parse(html);
        var mlv = new MeasureLengthVisitor();
        doc.traverse(mlv);

        Assertions.assertEquals(mlv.length, new DocumentTags(doc).textLength());
    }

    @Test
    void attrMatchingFollowsSelectorSemantics() {
        var doc = Jsoup.parse("""
                <html><head>
                <meta property="datePublished" content="a">
                <meta property="DATEPUBLISHED " content="b">
                <meta property="something-else" content="c">
                <meta content="d">
                </head></html>
                """);

        var expected = doc.select("meta[property=datePublished]");
        var actual = new DocumentTags(doc).metaTags()
                .stream()
                .filter(meta -> DocumentTags.attrIs(meta, "property", "datePublished"))
                .toList();

        Assertions.assertEquals(2, actual.size());
        Assertions.assertEquals(expected, actual);
    }
}
