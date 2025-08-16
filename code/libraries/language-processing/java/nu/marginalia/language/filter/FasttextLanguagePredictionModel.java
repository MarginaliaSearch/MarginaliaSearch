package nu.marginalia.language.filter;

import com.github.jfasttext.JFastText;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.DocumentLanguageData;

import java.util.Set;

/** A language prediction model that uses a FastText model to predict the language of a document */
public class FasttextLanguagePredictionModel implements LanguagePredictionModel {
    private final Set<String> permittedLanguageLabels = Set.of(
            "__label__en",
            "__label__sv"
    );
    private final JFastText jft = new JFastText();

    public FasttextLanguagePredictionModel(LanguageModels lm){
        jft.loadModel(lm.fasttextLanguageModel.toString());
    }

    @Override
    public double predictEnglish(DocumentLanguageData dld) {
        String prediction = jft.predict(dld.text());

        if (permittedLanguageLabels.contains(prediction)) return 1.0;
        else return 0.0;
    }

    @Override
    public boolean hasPoorAccuracy() {
        return false;
    }
}
