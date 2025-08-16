package nu.marginalia.language.filter;

import com.github.jfasttext.JFastText;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.DocumentLanguageData;

import java.util.Optional;

/** A language prediction model that uses a FastText model to predict the language of a document */
public class FasttextLanguagePredictionModel implements LanguagePredictionModel {

    private final JFastText jft = new JFastText();

    public FasttextLanguagePredictionModel(LanguageModels lm){
        jft.loadModel(lm.fasttextLanguageModel.toString());
    }

    public Optional<String> predictLanguage(DocumentLanguageData dld) {
        String prediction = jft.predict(dld.text());

        if (prediction.length() == "__label__??".length()) {
            return Optional.of(prediction.substring("__label__".length()));
        }

        return Optional.empty();
    }

    @Override
    public boolean hasPoorAccuracy() {
        return false;
    }
}
