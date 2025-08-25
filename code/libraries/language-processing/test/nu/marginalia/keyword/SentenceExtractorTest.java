package nu.marginalia.keyword;

import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.util.TestLanguageModels;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
class SentenceExtractorTest {
    static final LanguageModels lm = TestLanguageModels.getLanguageModels();

    static SentenceExtractor se;
    private static LanguageDefinition english;


    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        var config = new LanguageConfiguration(WmsaHome.getLanguageModels());
        se = new SentenceExtractor(config, WmsaHome.getLanguageModels());
        english = config.getLanguage("en");

    }

    public static void main(String... args) throws IOException, URISyntaxException, UnsupportedLanguageException {
        final LanguageModels lm = TestLanguageModels.getLanguageModels();

        var data = WmsaHome.getHomePath().resolve("test-data/");

        System.out.println("Running");

        var dict = new TermFrequencyDict(lm);
        var url = new EdgeUrl("https://memex.marginalia.nu/");
        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        for (;;) {
            long total = 0;
            for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
                var doc = Jsoup.parse(Files.readString(file.toPath()));
                long start = System.currentTimeMillis();
                var dld = se.extractSentences(doc);
                documentKeywordExtractor.extractKeywords(dld, "en", new LinkTexts(), url);
                total += (System.currentTimeMillis() - start);
            }
            System.out.println(total);
        }
    }

    @Test
    public void separatorExtraction() {
        seprateExtractor("Cookies, cream and shoes");
        seprateExtractor("Cookies");
        seprateExtractor("");
    }

    @Test
    public void testACDC() {
        var ret = se.extractSentence(english, "AC/DC is a rock band.", EnumSet.noneOf(HtmlTag.class));
        assertEquals("ac/dc", ret.wordsLowerCase[0]);
    }

    final Pattern p = Pattern.compile("([, ]+)");
    public void seprateExtractor(String sentence) {
        var matcher = p.matcher(sentence);

        Arrays.stream(p.split(sentence)).forEach(System.out::println);
        List<String> words = new ArrayList<>();
        List<String> separators = new ArrayList<>();

        int wordStart = 0;
        while (wordStart <= sentence.length()) {
            if (!matcher.find(wordStart)) {
                words.add(sentence.substring(wordStart));
                separators.add("S");
                break;
            }

            if (wordStart != matcher.start()) {
                words.add(sentence.substring(wordStart, matcher.start()));
                separators.add(sentence.substring(matcher.start(), matcher.end()).isBlank() ? "S" : "C");
            }
            wordStart = matcher.end();
        }

        System.out.println(words);
        System.out.println(separators);
    }
}