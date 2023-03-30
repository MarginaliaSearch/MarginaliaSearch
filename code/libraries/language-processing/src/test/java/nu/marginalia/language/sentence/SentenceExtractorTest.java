package nu.marginalia.language.sentence;

import nu.marginalia.WmsaHome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void testApostrophe() {
        var dld = sentenceExtractor.extractSentence("duke nuke 'em's big ol' big gun");
        assertEquals(7, dld.words.length);

        assertArrayEquals(new String[] { "duke", "nuke", "em's", "big", "ol", "big", "gun"}, dld.words);
        assertArrayEquals(new String[] { "duke", "nuke", "em", "big", "ol", "big", "gun"}, dld.wordsLowerCase);
    }
}