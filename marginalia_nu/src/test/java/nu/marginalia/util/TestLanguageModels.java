package nu.marginalia.util;

import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.configuration.WmsaHome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TestLanguageModels {
    private static final Path LANGUAGE_MODELS_DEFAULT = WmsaHome.getHomePath().resolve("model");

    public static Path getLanguageModelsPath() {
        final Path languageModelsHome = Optional.ofNullable(System.getenv("LANGUAGE_MODELS_HOME"))
                .map(Path::of)
                .orElse(LANGUAGE_MODELS_DEFAULT);

        if (!Files.isDirectory(languageModelsHome)) {
            throw new IllegalStateException("Could not find $LANGUAGE_MODELS_HOME, see doc/language-models.md");
        }
        return languageModelsHome;
    }

    public static LanguageModels getLanguageModels() {

        var languageModelsHome = getLanguageModelsPath();

        return new LanguageModels(
                languageModelsHome.resolve("ngrams.bin"),
                languageModelsHome.resolve("tfreq-generous-emstr.bin"),
                languageModelsHome.resolve("opennlp-sentence.bin"),
                languageModelsHome.resolve("English.RDR"),
                languageModelsHome.resolve("English.DICT"),
                languageModelsHome.resolve("opennlp-tokens.bin")
        );
    }
}
