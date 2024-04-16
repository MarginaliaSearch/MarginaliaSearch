package nu.marginalia.keyword;

import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class DocumentKeywordExtractorTest {

    DocumentKeywordExtractor extractor = new DocumentKeywordExtractor(
            new TermFrequencyDict(WmsaHome.getLanguageModels()),
            new NgramLexicon(WmsaHome.getLanguageModels()));
    SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

    @Test
    public void testWordPattern() {
        Assertions.assertTrue(extractor.matchesWordPattern("test"));
        Assertions.assertTrue(extractor.matchesWordPattern("1234567890abcde"));
        Assertions.assertFalse(extractor.matchesWordPattern("1234567890abcdef"));

        Assertions.assertTrue(extractor.matchesWordPattern("test-test-test-test-test"));
        Assertions.assertFalse(extractor.matchesWordPattern("test-test-test-test-test-test"));
        Assertions.assertTrue(extractor.matchesWordPattern("192.168.1.100/24"));
        Assertions.assertTrue(extractor.matchesWordPattern("std::vector"));
        Assertions.assertTrue(extractor.matchesWordPattern("c++"));
        Assertions.assertTrue(extractor.matchesWordPattern("m*a*s*h"));
        Assertions.assertFalse(extractor.matchesWordPattern("Stulpnagelstrasse"));
    }


    @Test
    public void testEmptyMetadata() throws URISyntaxException {
        var dld = se.extractSentences("""
                Some sample text, I'm not sure what even triggers this
                """, "A title perhaps?");
        var keywordBuilder = extractor.extractKeywords(dld, new EdgeUrl("https://www.example.com/invalid"));
        var keywords = keywordBuilder.build();

        var pointer = keywords.newPointer();
        while (pointer.advancePointer()) {
            if (pointer.getMetadata() == 0L) {
                System.out.println("Aha! " + pointer.getKeyword());
            }
        }

    }

    @Test
    public void testKeyboards2() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/keyboards.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        var keywords = extractor.extractKeywords(se.extractSentences(doc), new EdgeUrl("https://pmortensen.eu/world2/2021/12/24/rapoo-mechanical-keyboards-gotchas-and-setup/"));

        keywords.getWords().forEach((k, v) -> {
            if (k.contains("_")) {
                System.out.println(k + " " + new WordMetadata(v));
            }
        });
    }
    @Test
    public void testKeyboards() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/keyboards.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        var keywords = extractor.extractKeywords(se.extractSentences(doc), new EdgeUrl("https://pmortensen.eu/world2/2021/12/24/rapoo-mechanical-keyboards-gotchas-and-setup/"));
        System.out.println(keywords.getMetaForWord("mechanical"));
        System.out.println(keywords.getMetaForWord("keyboard"));
        System.out.println(keywords.getMetaForWord("keyboards"));

        System.out.println(new WordMetadata(8894889328781L));
        System.out.println(new WordMetadata(4294967297L));
        System.out.println(new WordMetadata(566820053975498886L));
        // -
        System.out.println(new WordMetadata(1198298103937L));
        System.out.println(new WordMetadata(1103808168065L));
    }

    @Test
    public void testMadonna() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/madonna.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        var keywords = extractor.extractKeywords(
                se.extractSentences(doc),
                new EdgeUrl("https://encyclopedia.marginalia.nu/article/Don't_Tell_Me_(Madonna_song)")
        );

        var keywordsBuilt = keywords.build();
        var ptr = keywordsBuilt.newPointer();

        Map<String, WordMetadata> dirtyAndBlues = new HashMap<>();

        while (ptr.advancePointer()) {
            if (Set.of("dirty", "blues").contains(ptr.getKeyword())) {
                Assertions.assertNull(
                        dirtyAndBlues.put(ptr.getKeyword(), new WordMetadata(ptr.getMetadata()))
                );
            }
        }

        Assertions.assertTrue(dirtyAndBlues.containsKey("dirty"));
        Assertions.assertTrue(dirtyAndBlues.containsKey("blues"));
        Assertions.assertNotEquals(
                dirtyAndBlues.get("dirty"),
                dirtyAndBlues.get("blues")
                );
    }

    @Test
    public void testSpam() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/spam.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        DocumentKeywordExtractor extractor = new DocumentKeywordExtractor(
                new TermFrequencyDict(WmsaHome.getLanguageModels()),
                new NgramLexicon(WmsaHome.getLanguageModels()));
        SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

        var keywords = extractor.extractKeywords(se.extractSentences(doc), new EdgeUrl("https://math.byu.edu/wiki/index.php/All_You_Need_To_Know_About_Earning_Money_Online"));
        System.out.println(keywords.getMetaForWord("knitting"));
    }
}