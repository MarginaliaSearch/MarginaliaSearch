package nu.marginalia.util;

import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TestLanguageModels {
    private static final Path LANGUAGE_MODELS_DEFAULT = Path.of("/home/vlofgren/Work/ngrams/");

    public static LanguageModels getLanguageModels() {

        final Path languageModelsHome = Optional.ofNullable(System.getenv("LANGUAGE_MODELS_HOME"))
                .map(Path::of)
                .orElse(LANGUAGE_MODELS_DEFAULT);

        if (!Files.isDirectory(languageModelsHome)) {
            throw new IllegalStateException("Could not find $LANGUAGE_MODELS_HOME, see doc/language-models.md");
        }

        return new LanguageModels(
                languageModelsHome.resolve("ngrams-generous-emstr.bin"),
                languageModelsHome.resolve("tfreq-generous-emstr.bin"),
                languageModelsHome.resolve("opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
                languageModelsHome.resolve("English.RDR"),
                languageModelsHome.resolve("English.DICT"),
                languageModelsHome.resolve("opennlp-tok.bin")
        );
    }
}
