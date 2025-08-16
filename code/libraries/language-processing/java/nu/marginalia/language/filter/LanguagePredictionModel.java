package nu.marginalia.language.filter;

import nu.marginalia.language.model.DocumentLanguageData;

import java.util.Optional;

public interface LanguagePredictionModel {
    Optional<String> predictLanguage(DocumentLanguageData dld);

    boolean hasPoorAccuracy();

}
