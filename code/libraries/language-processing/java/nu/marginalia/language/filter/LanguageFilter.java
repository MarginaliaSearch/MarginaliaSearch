package nu.marginalia.language.filter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.encoding.UnicodeRanges;
import nu.marginalia.language.model.DocumentLanguageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

@Singleton
public class LanguageFilter {

    private final Set<String> permittedLanguages = Set.of("en", "sv");

    private static final Logger logger = LoggerFactory.getLogger(LanguageFilter.class);

    private final LanguagePredictionModel languagePredictionModel;

    public Optional<String> predictLanguage(DocumentLanguageData dld) {
        return languagePredictionModel
                .predictLanguage(dld)
                .filter(permittedLanguages::contains);
    }

    @Inject
    public LanguageFilter(LanguageModels lm) {
        try {
            languagePredictionModel = new FasttextLanguagePredictionModel(lm);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isBlockedUnicodeRange(String data) {
        for (var range: UnicodeRanges.values()) {
            if (range.test(data))
                return true;
        }
        return false;
    }

}
