package nu.marginalia.keyword;

import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.language.model.WordSeparator;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.test.util.TestLanguageModels;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
class SentenceExtractorTest {
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    SentenceExtractor se = new SentenceExtractor(lm);

    @SneakyThrows
    public static void main(String... args) throws IOException {
        final LanguageModels lm = TestLanguageModels.getLanguageModels();

        var data = WmsaHome.getHomePath().resolve("test-data/");

        System.out.println("Running");

        SentenceExtractor se = new SentenceExtractor(lm);

        var dict = new TermFrequencyDict(lm);
        var url = new EdgeUrl("https://memex.marginalia.nu/");
        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        for (;;) {
            long total = 0;
            for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
                var doc = Jsoup.parse(Files.readString(file.toPath()));
                long start = System.currentTimeMillis();
                var dld = se.extractSentences(doc);
                documentKeywordExtractor.extractKeywords(dld, url);
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
        var ret = se.extractSentence("AC/DC is a rock band.");
        assertEquals("AC/DC", ret.words[0]);
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