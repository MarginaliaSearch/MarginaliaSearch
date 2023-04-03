package nu.marginalia.keyword;

import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Objects;

class DocumentKeywordExtractorTest {

    @Test
    public void testWordPattern() {
        DocumentKeywordExtractor extractor = new DocumentKeywordExtractor(null);

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
    public void testKeyboards() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/keyboards.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        DocumentKeywordExtractor extractor = new DocumentKeywordExtractor(new TermFrequencyDict(WmsaHome.getLanguageModels()));
        SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

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
}