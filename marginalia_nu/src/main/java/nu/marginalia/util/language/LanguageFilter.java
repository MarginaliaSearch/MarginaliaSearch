package nu.marginalia.util.language;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Singleton
public class LanguageFilter {

    private static final Set<String> interestingLanguages = Set.of("en", "en-us", "en-gb", "eng", "english");

    private static final Set<String> englishWords = new HashSet<>();
    private static final Logger logger = LoggerFactory.getLogger(LanguageFilter.class);
    static {
        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/en-1000"),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            for (;;) {
                String s = br.readLine();
                if (s == null) {
                    break;
                }
                englishWords.add(s.toLowerCase());
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    public double dictionaryAgreement(DocumentLanguageData dld) {
        Set<String> seenWords = new HashSet<>();
        int englishCount = 0;

        for (var sent : dld.sentences) {
            for (var word : sent.wordsLowerCase) {
                if (seenWords.add(word) && englishWords.contains(word)) {
                    englishCount++;
                }
            }
        }

        double englishAgreement = englishCount / (double) Math.min(seenWords.size(), englishWords.size());

        logger.debug("Agreement: {}", englishAgreement);

        return englishAgreement;
    }

    @Inject
    public LanguageFilter() {
    }

    public Optional<Boolean> isPageInterestingByHtmlTag(Document parsed) {
        return Optional.of(parsed.getElementsByTag("html"))
                .map(tag -> tag.attr("lang"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .map(interestingLanguages::contains);
    }

    public Optional<Boolean> isPageInterestingByMetaLanguage(Document parsed) {
        return parsed.getElementsByTag("meta").stream().filter(elem -> "content-language".equalsIgnoreCase(elem.attr("http-equiv")))
                .map(elem -> elem.attr("content"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .map(interestingLanguages::contains)
                .findAny();
    }

    public boolean isBlockedUnicodeRange(String data) {
        return Arrays.stream(UnicodeRanges.values())
                .parallel().anyMatch(range -> range.test(data));
    }
}
