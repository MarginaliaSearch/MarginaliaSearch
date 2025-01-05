package nu.marginalia.language.sentence;

import nu.marginalia.WmsaHome;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SentenceExtractorTest {
    private static SentenceExtractor sentenceExtractor;

    @BeforeAll
    public static void setUp() {
        sentenceExtractor = new SentenceExtractor(WmsaHome.getLanguageModels());
    }

    @Test
    void testParen() {
        var dld = sentenceExtractor.extractSentence("I am (very) tall", EnumSet.noneOf(HtmlTag.class));

        System.out.println(dld);
    }

    @Test
    void testCplusplus() {
        var dld = sentenceExtractor.extractSentence("std::vector", EnumSet.noneOf(HtmlTag.class));
        assertEquals(1, dld.length());
        assertEquals("std::vector", dld.wordsLowerCase[0]);
    }

    @Test
    void testPHP() {
        var dld = sentenceExtractor.extractSentence("$_GET", EnumSet.noneOf(HtmlTag.class));
        assertEquals(1, dld.length());
        assertEquals("$_get", dld.wordsLowerCase[0]);
    }

    @Test
    void testPolishArtist() {
        var dld = sentenceExtractor.extractSentence("Uklański", EnumSet.noneOf(HtmlTag.class));

        assertEquals(1, dld.wordsLowerCase.length);
        assertEquals("uklanski", dld.wordsLowerCase[0]);
    }

    @Test
    void testJava() {
        var dld = sentenceExtractor.extractSentence("Foreign Function & Memory API", EnumSet.noneOf(HtmlTag.class));

        assertEquals(4, dld.wordsLowerCase.length);
        assertArrayEquals(new String[] {"foreign", "function", "memory", "api"}, dld.wordsLowerCase);
    }

    @Test
    void testJavaFile() {

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("html/jep.html"),
                "Could not load word frequency table"))
        {
            var doc = Jsoup.parse(new String(resource.readAllBytes()));
            var dld = sentenceExtractor.extractSentences(doc);
            for (var sent : dld) {
                System.out.println(sent);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSpamFile() {

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("html/spam.html"),
                "Could not load word frequency table"))
        {
            var doc = Jsoup.parse(new String(resource.readAllBytes()));
            var dld = sentenceExtractor.extractSentences(doc);
            for (var sent : dld) {
                System.out.println(sent);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    void testApostrophe() {
        var dld = sentenceExtractor.extractSentence("duke nuke 'em's big ol' big gun", EnumSet.noneOf(HtmlTag.class));
        assertEquals(7, dld.wordsLowerCase.length);

        assertArrayEquals(new String[] { "duke", "nuke", "em", "big", "ol", "big", "gun"}, dld.wordsLowerCase);
    }
}