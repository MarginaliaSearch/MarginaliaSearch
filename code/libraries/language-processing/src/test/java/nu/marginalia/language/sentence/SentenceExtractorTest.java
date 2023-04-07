package nu.marginalia.language.sentence;

import nu.marginalia.WmsaHome;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SentenceExtractorTest {
    private static SentenceExtractor sentenceExtractor;

    @BeforeAll
    public static void setUp() {
        sentenceExtractor = new SentenceExtractor(WmsaHome.getLanguageModels());
    }

    @Test
    void testParen() {
        var dld = sentenceExtractor.extractSentence("I am (very) tall");

        System.out.println(dld);
    }

    @Test
    void testPolishArtist() {
        var dld = sentenceExtractor.extractSentence("Uklański");

        assertEquals(1, dld.words.length);
        assertEquals("Uklanski", dld.words[0]);
        assertEquals("uklanski", dld.wordsLowerCase[0]);
    }

    @Test
    void testJava() {
        var dld = sentenceExtractor.extractSentence("Foreign Function & Memory API");

        assertEquals(4, dld.words.length);
        assertArrayEquals(new String[] {"Foreign", "Function", "Memory", "API"}, dld.words);
    }

    @Test
    void testJavaFile() {

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("html/jep.html"),
                "Could not load word frequency table"))
        {
            var doc = Jsoup.parse(new String(resource.readAllBytes()));
            var dld = sentenceExtractor.extractSentences(doc);
            for (var sent : dld.sentences) {
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
            for (var sent : dld.sentences) {
                System.out.println(sent);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    void testApostrophe() {
        var dld = sentenceExtractor.extractSentence("duke nuke 'em's big ol' big gun");
        assertEquals(7, dld.words.length);

        assertArrayEquals(new String[] { "duke", "nuke", "em's", "big", "ol", "big", "gun"}, dld.words);
        assertArrayEquals(new String[] { "duke", "nuke", "em", "big", "ol", "big", "gun"}, dld.wordsLowerCase);
    }
}