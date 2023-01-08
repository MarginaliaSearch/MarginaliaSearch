package nu.marginalia.wmsa.edge.converting.processor.logic;

import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateSniffer;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.heuristic.PubDateHeuristicDOMParsingPass2;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class PubDateSnifferTest {

    PubDateSniffer dateSniffer = new PubDateSniffer();

    @Test
    public void testGetYearFromText() {
        var ret = PubDateParser.dateFromHighestYearLookingSubstring("&copy; 2005-2010 Bob Dobbs");
        assertTrue(ret.isPresent());
        assertEquals(2007, ret.get().year());

        ret = PubDateParser.dateFromHighestYearLookingSubstring("&copy; 99 Bob Dobbs");
        assertFalse(ret.isPresent());

        ret = PubDateParser.dateFromHighestYearLookingSubstring("&copy; 1939 Bob Dobbs");
        assertFalse(ret.isPresent());

        ret = PubDateParser.dateFromHighestYearLookingSubstring("In the year 2525, if man is still alive");
        assertFalse(ret.isPresent());
    }

    @Test
    public void testParse() {
        var ret = PubDateParser.attemptParseDate("2022-01-01");
        assertTrue(ret.isPresent());
        assertEquals("2022-01-01", ret.get().dateIso8601());
        assertEquals(2022, ret.get().year());

        ret = PubDateParser.attemptParseDate("2022-08-24T14:39:14Z");
        assertTrue(ret.isPresent());
        assertEquals("2022-08-24", ret.get().dateIso8601());
        assertEquals(2022, ret.get().year());

        ret = PubDateParser.attemptParseDate("2022-08-24T14:39:14");
        assertTrue(ret.isPresent());
        assertEquals("2022-08-24", ret.get().dateIso8601());
        assertEquals(2022, ret.get().year());

        ret = PubDateParser.attemptParseDate("Sun, 21 Oct 2018 12:16:24 GMT");
        assertTrue(ret.isPresent());
        assertEquals("2018-10-21", ret.get().dateIso8601());
        assertEquals(2018, ret.get().year());

        ret = PubDateParser.attemptParseDate("July 13, 2006");
        assertTrue(ret.isPresent());
        assertEquals(2006, ret.get().year());

    }


    @Test
    public void testHtml5A() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <article>
                        <time pubdate="pubdate" datetime="2022-08-24">time</time>
                        Wow, sure lor 'em boss
                        </article>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2022-08-24", ret.dateIso8601());
    }

    @Test
    public void testHtml5B() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <time>2022-08-24</time>
                        Wow, sure lor 'em boss
                        </article>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2022-08-24", ret.dateIso8601());
    }

    @Test
    public void testHtml5C() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <time class="published" datetime="July 13, 2006">July 13, 2006</time>
                        Wow, sure lor 'em boss
                        </article>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals(2006, ret.year());
    }

    @Test
    public void testProblemCases() throws IOException, URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse(Files.readString(WmsaHome.getHomePath().resolve("test-data/The Switch to Linux Begins .html"))), EdgeHtmlStandard.HTML5, true);

        assertFalse(ret.isEmpty());
        assertEquals(2006, ret.year());

        ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse(Files.readString(WmsaHome.getHomePath().resolve("test-data/Black Hat USA 2010 Understanding and Deploying DNSSEC by Paul Wouters and Patrick Nauber.html"))), EdgeHtmlStandard.XHTML, true);

        assertFalse(ret.isEmpty());
        assertEquals(2010, ret.year());
    }

    @Test
    public void testGuessYear() {
        System.out.println(PubDateParser.guessYear(2010, 2020));
        System.out.println(PubDateParser.guessYear(2010, 2020));
        System.out.println(PubDateParser.guessYear(2010, 2020));
        System.out.println(PubDateParser.guessYear(2010, 2020));
        System.out.println(PubDateParser.guessYear(2010, 2020));
    }

    @Test
    public void testMicrodata() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <meta itemprop="datePublished" content="2022-08-24" />
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2022-08-24", ret.dateIso8601());
    }

    @Test
    public void testRDFa() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <meta property="datePublished" content="2022-08-24" />
                        """),EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2022-08-24", ret.dateIso8601());
    }

    @Test
    public void testLD() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <script type="application/ld+json">{"@context":"https:\\/\\/schema.org","@type":"Article","name":"In the Year 2525","url":"https:\\/\\/en.wikipedia.org\\/wiki\\/In_the_Year_2525","sameAs":"http:\\/\\/www.wikidata.org\\/entity\\/Q145269","mainEntity":"http:\\/\\/www.wikidata.org\\/entity\\/Q145269","author":{"@type":"Organization","name":"Contributors to Wikimedia projects"},"publisher":{"@type":"Organization","name":"Wikimedia Foundation, Inc.","logo":{"@type":"ImageObject","url":"https:\\/\\/www.wikimedia.org\\/static\\/images\\/wmf-hor-googpub.png"}},"datePublished":"2004-08-24T14:39:14Z","dateModified":"2022-10-20T11:54:37Z","image":"https:\\/\\/upload.wikimedia.org\\/wikipedia\\/commons\\/4\\/4a\\/In_the_Year_2525_by_Zager_and_Evans_US_vinyl_Side-A_RCA_release.png","headline":"song written and compsoed by Rick Evans, originally recorded by Zager and Evans and released in 1969"}</script><script type="application/ld+json">{"@context":"https:\\/\\/schema.org","@type":"Article","name":"In the Year 2525","url":"https:\\/\\/en.wikipedia.org\\/wiki\\/In_the_Year_2525","sameAs":"http:\\/\\/www.wikidata.org\\/entity\\/Q145269","mainEntity":"http:\\/\\/www.wikidata.org\\/entity\\/Q145269","author":{"@type":"Organization","name":"Contributors to Wikimedia projects"},"publisher":{"@type":"Organization","name":"Wikimedia Foundation, Inc.","logo":{"@type":"ImageObject","url":"https:\\/\\/www.wikimedia.org\\/static\\/images\\/wmf-hor-googpub.png"}},"datePublished":"2004-08-24T14:39:14Z","dateModified":"2022-10-20T11:54:37Z","image":"https:\\/\\/upload.wikimedia.org\\/wikipedia\\/commons\\/4\\/4a\\/In_the_Year_2525_by_Zager_and_Evans_US_vinyl_Side-A_RCA_release.png","headline":"song written and compsoed by Rick Evans, originally recorded by Zager and Evans and released in 1969"}</script>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2004-08-24", ret.dateIso8601());
    }

    @Test
    public void testPath() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/articles/2022/04/how-to-detect-dates"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <title>No date in the HTML</title>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertNull(ret.dateIso8601());
        assertEquals(2022, ret.year());
    }

    @Test
    public void testHeader() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("content-type: application/pdf\netag: \"4fc0ba8a7f5090b6fa6be385dca206ec\"\nlast-modified: Thu, 03 Feb 2022 19:22:58 GMT\ncontent-length: 298819\ndate: Wed, 24 Aug 2022 19:48:52 GMT\ncache-control: public, no-transform, immutable, max-age\u003d31536000\naccess-control-expose-headers: Content-Length,Content-Disposition,Content-Range,Etag,Server-Timing,Vary,X-Cld-Error,X-Content-Type-Options\naccess-control-allow-origin: *\naccept-ranges: bytes\ntiming-allow-origin: *\nserver: Cloudinary\nstrict-transport-security: max-age\u003d604800\nx-content-type-options: nosniff\nserver-timing: akam;dur\u003d25;start\u003d2022-08-24T19:48:52.519Z;desc\u003dmiss,rtt;dur\u003d19,cloudinary;dur\u003d129;start\u003d2022-08-23T06:35:17.331Z\n",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <title>No date in the HTML</title>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertEquals("2022-02-03", ret.dateIso8601());
    }


    @Test
    public void testDOM() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <p>Published 2003, updated 2022</p>
                        """), EdgeHtmlStandard.HTML5, true);

        assertFalse(ret.isEmpty());
        assertNull(ret.dateIso8601());
        assertEquals(2015, ret.year());
    }

    @Test
    public void testCandidate() {
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("(C) 2007"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("(C) 2007-01-01"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("(C) 01-01.2007"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("Only $1999"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("1998B"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("1998B"));
        System.out.println(PubDateHeuristicDOMParsingPass2.isPossibleCandidate("2010 black hat ™"));
    }

    @Test
    public void testOldInvision() throws URISyntaxException {
        var ret = dateSniffer.getPubDate("",
                new EdgeUrl("https://www.example.com/"),
                Jsoup.parse("""
                        <!doctype html>
                        <html>
                        <div style="float: left;">&nbsp;<b>Post subject:</b> Keyboards.</div><div style="float: right;"><span class="postdetails"><b><img src="./styles/subsilver2/imageset/icon_post_target.gif" width="12" height="9" alt="Post" title="Post" /> <a  href="./viewtopic.php?p=34580&amp;sid=cf0c13dedebb4fea1f03fa73e510cd9f#p34580">#1</a></b></span>&nbsp;<b>Posted:</b> Sun Oct 03, 2010 5:37 pm&nbsp;</div>
                        """), EdgeHtmlStandard.UNKNOWN, true);

        assertFalse(ret.isEmpty());
        assertNull(ret.dateIso8601());
        assertEquals(2010, ret.year());
    }
}