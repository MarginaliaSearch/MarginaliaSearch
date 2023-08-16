package nu.marginalia.converting.language;

import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.encoding.UnicodeRanges;
import nu.marginalia.language.model.DocumentLanguageData;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;

@Singleton
public class LanguageFilter {

    private static final Set<String> interestingLanguages = Set.of("en", "en-us", "en-gb", "eng", "english");

    private static final Logger logger = LoggerFactory.getLogger(LanguageFilter.class);

    private final LanguagePredictionModel languagePredictionModel;


    /** Returns the probability the language is in English */
    public double dictionaryAgreement(DocumentLanguageData dld) {
        return languagePredictionModel.predictEnglish(dld);
    }

    @Inject
    @SneakyThrows
    public LanguageFilter(LanguageModels lm) {
        try {
            if (Boolean.getBoolean("disable-fasttext")) {
                languagePredictionModel = new UngaBungaLanguagePredictionModel();
            }
            else {
                languagePredictionModel = new FasttextLanguagePredictionModel(lm);
            }
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
        if (!languagePredictionModel.hasPoorAccuracy()) {
            return false;
        }

        for (var range: UnicodeRanges.values()) {
            if (range.test(data))
                return true;
        }
        return false;
    }

}
