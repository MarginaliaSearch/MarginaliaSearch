package nu.marginalia.language.filter;

import com.github.jfasttext.JFastText;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.DocumentLanguageData;

/** A language prediction model that uses a FastText model to predict the language of a document */
public class FasttextLanguagePredictionModel implements LanguagePredictionModel {
    private final JFastText jft = new JFastText();

    public FasttextLanguagePredictionModel(LanguageModels lm) throws Exception {
        jft.loadModel(lm.fasttextLanguageModel.toString());
    }

    @Override
    public double predictEnglish(DocumentLanguageData dld) {
        if ("__label__en".equals(jft.predict(dld.text()))) {
            return 1.0;
        }
        return 0.;
    }

    @Override
    public boolean hasPoorAccuracy() {
        return false;
    }
}
