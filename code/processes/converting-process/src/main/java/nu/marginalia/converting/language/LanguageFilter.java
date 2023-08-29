package nu.marginalia.converting.language;

import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.encoding.UnicodeRanges;
import nu.marginalia.language.model.DocumentLanguageData;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.Set;

@Singleton
public class LanguageFilter {

    private static final Set<String> interestingLanguages = Set.of("en", "en-us", "en-gb", "eng", "english");

    private static final Logger logger = LoggerFactory.getLogger(LanguageFilter.class);

    private final LanguagePredictionModel languagePredictionModel1;
    private final LanguagePredictionModel languagePredictionModel2;


    /** Returns the probability the language is in English */
    public double dictionaryAgreement(DocumentLanguageData dld) {
        if (languagePredictionModel1.predictEnglish(dld) < 0.1)
            return 0;

        return languagePredictionModel2.predictEnglish(dld);
    }

    @Inject
    @SneakyThrows
    public LanguageFilter(LanguageModels lm) {
        try {
            languagePredictionModel1 = new UngaBungaLanguagePredictionModel();
            languagePredictionModel2 = new FasttextLanguagePredictionModel(lm);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Boolean> isPageInterestingByHtmlTag(Document parsed) {
        return Optional.of(parsed.getElementsByTag("html"))
                .map(tag -> tag.attr("lang"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .map(interestingLanguages::contains);
    }


    public boolean isBlockedUnicodeRange(String data) {
        for (var range: UnicodeRanges.values()) {
            if (range.test(data))
                return true;
        }
        return false;
    }

}
