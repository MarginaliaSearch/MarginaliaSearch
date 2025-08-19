package nu.marginalia.language.filter;

import com.github.jfasttext.JFastText;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.encoding.UnicodeRanges;
import nu.marginalia.language.model.DocumentLanguageData;

import java.util.Optional;

@Singleton
public class LanguageFilter {

    private final LanguageConfiguration languageConfiguration;
    private final JFastText jft = new JFastText();

    @Inject
    public LanguageFilter(LanguageModels lm, LanguageConfiguration languageConfiguration) {
        this.languageConfiguration = languageConfiguration;
        jft.loadModel(lm.fasttextLanguageModel.toString());
    }

    public Optional<String> predictLanguage(DocumentLanguageData dld) {
        String prediction = jft.predict(dld.text());

        if (prediction.length() == "__label__??".length()) {
            String isoCode = prediction.substring("__label__".length());

            LanguageConfiguration.LanguageDefinition config = languageConfiguration.getLanguage(isoCode);

            if (config != null)
                return Optional.of(isoCode);
        }

        return Optional.empty();
    }


    public boolean isBlockedUnicodeRange(String data) {
        for (var range: UnicodeRanges.values()) {
            if (range.test(data))
                return true;
        }
        return false;
    }

}
