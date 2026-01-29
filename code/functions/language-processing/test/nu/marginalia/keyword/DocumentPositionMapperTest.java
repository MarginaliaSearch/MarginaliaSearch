package nu.marginalia.keyword;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.keyword.model.DocumentWordSpan;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static nu.marginalia.keyword.DocumentPositionMapper.matchesWordPattern;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPositionMapperTest {
    private static LanguageDefinition english;
    private DocumentPositionMapper positionMapper;
    static SentenceExtractor se;

    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        var config = new LanguageConfiguration(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Experimental());
        se = new SentenceExtractor(config, WmsaHome.getLanguageModels());
        english = config.getLanguage("en");
    }

    @BeforeEach
    public void setUp() {
        positionMapper = new DocumentPositionMapper();
    }

    @Test
    public void testWordPattern() {
        assertTrue(matchesWordPattern("test"));
        assertTrue(matchesWordPattern("1234567890abcde"));
        assertFalse(matchesWordPattern("1234567890abcdef"));

        assertTrue(matchesWordPattern("test-test-test-test-test"));
        assertFalse(matchesWordPattern("test-test-test-test-test-test-test-test-test-test"));
        assertTrue(matchesWordPattern("192.168.1.100/24"));
        assertTrue(matchesWordPattern("std::vector"));
        assertTrue(matchesWordPattern("std::vector::push_back"));

        assertTrue(matchesWordPattern("c++"));
        assertTrue(matchesWordPattern("m*a*s*h"));
        assertFalse(matchesWordPattern("Stulpnagelstrasse"));
    }

    @Test
    public void testBasic() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();
        DocumentLanguageData dld = new DocumentLanguageData(english,
                se.extractSentencesFromString(english, "I am a teapot, short and stout", EnumSet.of(HtmlTag.CODE)),
                "I am a teapot"
        );

        int pos = positionMapper.mapDocumentPositions(keywordsBuilder, Mockito.mock(KeywordMetadata.class), dld);

        assertEquals(8, pos);
        assertEquals(IntList.of(1), keywordsBuilder.wordToPos.get("i"));
        assertEquals(IntList.of(2), keywordsBuilder.wordToPos.get("am"));
        assertEquals(IntList.of(3), keywordsBuilder.wordToPos.get("a"));
        assertEquals(IntList.of(4), keywordsBuilder.wordToPos.get("teapot"));
        assertEquals(IntList.of(5), keywordsBuilder.wordToPos.get("short"));
        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("and"));
        assertEquals(IntList.of(7), keywordsBuilder.wordToPos.get("stout"));

        var codeSpans = keywordsBuilder.wordSpans.get(HtmlTag.CODE);
        assertEquals(1, codeSpans.size());
        var codeSpan = codeSpans.getFirst();

        assertEquals(1, codeSpan.start());
        assertEquals(8, codeSpan.end());
    }


    @Test
    public void testLinksSingleWord1Rep() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString(english, "Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 1 });

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("zelda"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(1, linkTextSpans.size());
        var codeSpan = linkTextSpans.getFirst();

        assertEquals(6, codeSpan.start());
        assertEquals(7, codeSpan.end());
    }

    @Test
    public void testLinksSingleWord2Reps() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString(english, "Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 4 }); // This will become 2 repetitions, formula is ~ sqrt(counts)

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6, 9), keywordsBuilder.wordToPos.get("zelda"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(7, span.end());

        span = linkTextSpans.get(1);

        assertEquals(9, span.start());
        assertEquals(10, span.end());
    }

    @Test
    public void testLinksTwoWords2Reps() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString(english, "Zelda II", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 4 });

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6, 10), keywordsBuilder.wordToPos.get("zelda"));
        assertEquals(IntList.of(7, 11), keywordsBuilder.wordToPos.get("ii"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(8, span.end());

        span = linkTextSpans.get(1);

        assertEquals(10, span.start());
        assertEquals(12, span.end());
    }


    @Test
    public void testLinksTwoSent1Word1Rep() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences1 = se.extractSentencesFromString(english, "Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        var sentences2 = se.extractSentencesFromString(english, "Link", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences1.size());
        assertEquals(1, sentences2.size());
        TIntList counts = new TIntArrayList(new int[] { 1, 1 });

        List<DocumentSentence> sentencesAll = new ArrayList<>();
        sentencesAll.addAll(sentences1);
        sentencesAll.addAll(sentences2);

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentencesAll, counts));

        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("zelda"));
        assertEquals(IntList.of(9), keywordsBuilder.wordToPos.get("link"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(7, span.end());

        span = linkTextSpans.get(1);

        assertEquals(9, span.start());
        assertEquals(10, span.end());
    }

    @Test
    void testSimpleWord() {
        assertTrue(matchesWordPattern("hello"));
        assertTrue(matchesWordPattern("a"));
        assertTrue(matchesWordPattern("123"));
        assertTrue(matchesWordPattern("abc123"));
    }

    @Test
    void testWithSingleSeparator() {
        assertTrue(matchesWordPattern("hello-world"));
        assertTrue(matchesWordPattern("test.case"));
        assertTrue(matchesWordPattern("foo_bar"));
        assertTrue(matchesWordPattern("path/to"));
        assertTrue(matchesWordPattern("a:b"));
        assertTrue(matchesWordPattern("x+y"));
        assertTrue(matchesWordPattern("a*b"));
    }

    @Test
    void testMaxInitialLength() {
        assertTrue(matchesWordPattern("123456789012345"));
        assertTrue(matchesWordPattern("123456789012345-abc"));
    }

    @Test
    void testMaxSeparatorGroups() {
        assertTrue(matchesWordPattern("a-b-c-d-e-f-g-h-i"));
        assertTrue(matchesWordPattern("1.2.3.4.5.6.7.8.9"));
    }

    @Test
    void testMaxLengthAfterSeparator() {
        assertTrue(matchesWordPattern("a-1234567890"));
    }

    @Test
    void testAllSeparatorTypes() {
        assertTrue(matchesWordPattern("a.b"));
        assertTrue(matchesWordPattern("a-b"));
        assertTrue(matchesWordPattern("a_b"));
        assertTrue(matchesWordPattern("a/b"));
        assertTrue(matchesWordPattern("a:b"));
        assertTrue(matchesWordPattern("a+b"));
        assertTrue(matchesWordPattern("a*b"));
    }

    @Test
    void testTrailingSeparator() {
        assertTrue(matchesWordPattern("hello-"));
        assertTrue(matchesWordPattern("test."));
        assertTrue(matchesWordPattern("abc_"));
        assertTrue(matchesWordPattern("a-b-"));
    }

    @Test
    void testEmptyString() {
        assertFalse(matchesWordPattern(""));
    }

    @Test
    void testStartsWithSeparator() {
        assertFalse(matchesWordPattern("-hello"));
        assertFalse(matchesWordPattern(".test"));
        assertFalse(matchesWordPattern("_abc"));
    }

    @Test
    void testConsecutiveSeparators() {
        assertTrue(matchesWordPattern("hello--world"));
        assertFalse(matchesWordPattern("a....b"));
        assertTrue(matchesWordPattern("test-_case"));
    }

    @Test
    void testTooLong() {
        // More than 48 characters total
        assertFalse(matchesWordPattern("a".repeat(49)));
        assertFalse(matchesWordPattern("a".repeat(25) + "-" + "b".repeat(25)));
    }

    @Test
    void testInitialSegmentTooLong() {
        // More than 15 chars before first separator
        assertFalse(matchesWordPattern("1234567890123456"));
        assertFalse(matchesWordPattern("1234567890123456-abc"));
    }

    @Test
    void testSegmentAfterSeparatorTooLong() {
        // More than 10 chars after separator
        assertFalse(matchesWordPattern("a-12345678901"));
    }

    @Test
    void testTooManySeparatorGroups() {
        // More than 8 separator groups
        assertFalse(matchesWordPattern("a-b-c-d-e-f-g-h-i-j"));
        assertFalse(matchesWordPattern("1.2.3.4.5.6.7.8.9.10"));
    }

    @Test
    void testInvalidCharacters() {
        assertFalse(matchesWordPattern("hello world"));
        assertFalse(matchesWordPattern("a$b"));
        assertFalse(matchesWordPattern("x&y"));
    }

    @Test
    void testSpecialCharactersNotInAllowedSet() {
        assertFalse(matchesWordPattern("a,b"));
        assertFalse(matchesWordPattern("a;b"));
        assertFalse(matchesWordPattern("a!b"));
        assertFalse(matchesWordPattern("a?b"));
    }

    // Edge cases with lengths
    @Test
    void testExactly48Chars() {
        assertTrue(matchesWordPattern("a".repeat(15) + "-" + "b".repeat(10) + "-" + "c".repeat(10) + "-" + "d".repeat(10)));
    }

    @Test
    void testBoundaryLengths() {
        assertTrue(matchesWordPattern("a".repeat(15)));
        assertTrue(matchesWordPattern("a-" + "b".repeat(10)));
        String eightGroups = "a";
        for (int i = 0; i < 8; i++) {
            eightGroups += "-b";
        }
        assertTrue(matchesWordPattern(eightGroups));
    }

    // Unicode handling
    @Test
    void testUnicodeCharacters() {
        assertTrue(matchesWordPattern("café"));
        assertTrue(matchesWordPattern("naïve"));
        assertTrue(matchesWordPattern("é".repeat(15)));
    }

    @Test
    void testMixedCase() {
        assertTrue(matchesWordPattern("HelloWorld"));
        assertTrue(matchesWordPattern("Test-Case"));
        assertTrue(matchesWordPattern("FOO_bar_123"));
    }

    @Test
    void testRealisticPatterns() {
        assertTrue(matchesWordPattern("example.com"));
        assertTrue(matchesWordPattern("sub.domain.co"));
        assertTrue(matchesWordPattern("file.txt"));
        assertTrue(matchesWordPattern("my-file_v2.dat"));
        assertTrue(matchesWordPattern("v1.2.3"));
        assertTrue(matchesWordPattern("2024-01-01"));
        assertTrue(matchesWordPattern("my_function"));
        assertTrue(matchesWordPattern("className"));
    }

}