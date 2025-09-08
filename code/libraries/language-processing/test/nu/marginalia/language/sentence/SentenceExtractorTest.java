package nu.marginalia.language.sentence;

import nu.marginalia.WmsaHome;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SentenceExtractorTest {
    private static SentenceExtractor sentenceExtractor;
    private static LanguageConfiguration languageConfig;

    @BeforeAll
    public static void setUp() throws IOException, ParserConfigurationException, SAXException {
        languageConfig = new LanguageConfiguration(WmsaHome.getLanguageModels());
        sentenceExtractor = new SentenceExtractor(languageConfig, WmsaHome.getLanguageModels());
    }

    @Test
    void testParen() {
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("en"),"I am (very) tall", EnumSet.noneOf(HtmlTag.class));

        System.out.println(dld);
    }

    @Test
    void testCplusplus() {
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("en"), "std::vector", EnumSet.noneOf(HtmlTag.class));
        assertEquals(1, dld.length());
        assertEquals("std::vector", dld.wordsLowerCase[0]);
    }

    @Test
    void testPHP() {
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("en"), "$_GET", EnumSet.noneOf(HtmlTag.class));
        assertEquals(1, dld.length());
        assertEquals("$_get", dld.wordsLowerCase[0]);
    }

    @Test
    void testPolishArtist() {
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("en"),"Uklański", EnumSet.noneOf(HtmlTag.class));

        assertEquals(1, dld.wordsLowerCase.length);
        assertEquals("uklanski", dld.wordsLowerCase[0]);
    }

    @Test
    void testJava() {
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("en"), "Foreign Function & Memory API", EnumSet.noneOf(HtmlTag.class));

        System.out.println(Arrays.toString(dld.wordsLowerCase));

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

        } catch (IOException | UnsupportedLanguageException e) {
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

        } catch (IOException | UnsupportedLanguageException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    void testApostrophe() {
        var lang = Objects.requireNonNull(languageConfig.getLanguage("en"));

        var dld = sentenceExtractor.extractSentence(lang, "duke nuke 'em's big ol' big gun", EnumSet.noneOf(HtmlTag.class));
        assertEquals(7, dld.wordsLowerCase.length);

        assertArrayEquals(new String[] { "duke", "nuke", "em", "big", "ol", "big", "gun"}, dld.wordsLowerCase);
    }
}