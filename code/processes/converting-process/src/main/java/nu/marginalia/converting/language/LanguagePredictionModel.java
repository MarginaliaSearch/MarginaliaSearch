package nu.marginalia.converting.language;

import nu.marginalia.language.model.DocumentLanguageData;

public interface LanguagePredictionModel {
    /** Returns the probability the language is in English */
    double predictEnglish(DocumentLanguageData dld);

    boolean hasPoorAccuracy();

}
