package nu.marginalia.language.sentence;

import nu.marginalia.WmsaHome;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
        languageConfig = new LanguageConfiguration(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Experimental());
        sentenceExtractor = new SentenceExtractor(languageConfig, WmsaHome.getLanguageModels());
    }

    @Test
    @Disabled
    void testFarsi() {
        String text = """
                فارسی دری، در طولِ تاریخ، زبانِ فرهنگی و ارجمند امپراتوری‌های پرشماری در آسیای غربی، میانه و جنوبی بوده است. این زبان تأثیرات بزرگی را بر زبان‌های همسایه خویش، از جمله دیگر زبان‌های ایرانی، زبان‌های ترکی (به ویژه ازبکی و آذربایجانی)، ارمنی، گرجی و زبان‌های هندوآریایی (به ویژه اردو) گذاشته است. فارسی بر عربی نیز تأثیر گذاشته و از آن تأثیر پذیرفته است. این زبان حتی در میان کسانی که گویشور بومی آن نبوده‌اند، همانند ترکان عثمانی در امپراتوری عثمانی یا پشتون‌ها در افغانستان، و هند در دوره گورکانیان برای دورانی زبان رسمی دیوان‌سالاری بوده است.
                """;
        var dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("fa"), text, EnumSet.noneOf(HtmlTag.class));

        String text2 = " (به ویژه اردو) ";
        dld = sentenceExtractor.extractSentence(languageConfig.getLanguage("fa"), text2, EnumSet.noneOf(HtmlTag.class));
        System.out.println(dld);
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