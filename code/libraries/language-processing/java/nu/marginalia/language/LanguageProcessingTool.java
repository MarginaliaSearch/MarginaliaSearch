package nu.marginalia.language;

import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LanguageProcessingTool extends Jooby {
    private static final Logger logger = LoggerFactory.getLogger(LanguageProcessingTool.class);
    private final ThreadLocalSentenceExtractorProvider  sentenceExtractorProvider;

    static void main(String[] args) {
        Jooby.runApp(args, LanguageProcessingTool::new);
    }

    public LanguageProcessingTool() {
        try {
            LanguageModels languageModels = getLanguageModels();

            sentenceExtractorProvider = new ThreadLocalSentenceExtractorProvider(
                    new LanguageConfiguration(languageModels),
                    languageModels
            );
            Path basePath = Path.of("code/libraries/language-processing/").toAbsolutePath();
            System.out.println("Base path: " + basePath);

            if (Files.exists(basePath.resolve("resources/ltt/jte")))
                install(new nu.marginalia.service.server.jte.JteModule(basePath.resolve("resources/ltt/jte")));
            if (Files.exists(basePath.resolve("resources/ltt/static")))
                assets("/*", basePath.resolve("resources/ltt/static"));

            get("/", this::handleKeywords);
            post("/", this::handleKeywords);
        }
        catch (Exception ex) {
            logger.error("Failed to initialize LanguageProcessingTool", ex);
            throw new RuntimeException(ex);
        }
    }
    // Assign colors to the POS tags

    @NotNull
    private ModelAndView<?> handleKeywords(Context context) {
        if ("GET".equals(context.getMethod())) {
           return new MapModelAndView("keywords.jte")
                   .put("textSample", "");
        }
        else if (!"POST".equals(context.getMethod())) {
            throw new IllegalArgumentException("Invalid method");
        }

        String textSample = context.form("textSample").value();
        var dld = sentenceExtractorProvider.get().extractSentences(textSample);
        Map<String, String> posStyles = posTagStyles(dld);

        System.out.println(posStyles);

        return new MapModelAndView("keywords.jte")
                .put("textSample", textSample)
                .put("language", dld.language())
                .put("tagColors", posStyles)
                .put("sentences", dld.sentences());
    }

    public static Map<String, String> posTagStyles(DocumentLanguageData dld) {
        Map<String, String> styles = new HashMap<>();

        // we sort them first to ensure the most common tags are guaranteed to have
        // the largest difference between colors

        Map<String, Integer> counts = new HashMap<>();
        for (var sentence : dld.sentences()) {
            for (var tag : sentence.posTags) {
                counts.merge(tag, 1, Integer::sum);
            }
        }

        List<String> posTagsByCount =  counts
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();


        for (int i = 0; i < posTagsByCount.size(); i++) {
            String style = "text-" + switch (i&0x7) {
                case 0 -> "red";
                case 1 -> "green";
                case 2 -> "blue";
                case 3 -> "yellow";
                case 4 -> "purple";
                case 5 -> "cyan";
                case 6 -> "pink";
                default -> "gray";
            }+"-"+switch((i/8) & 3) {
                case 0 -> "900";
                case 3 -> "500";
                case 1 -> "750";
                case 2 -> "400";
                default -> "300";
            };
            styles.put(posTagsByCount.get(i), style);
        }
        return styles;
    }

    private static final Path LANGUAGE_MODELS_DEFAULT = WmsaHome.getHomePath().resolve("model");
    private static Path getLanguageModelsPath() {
        final Path languageModelsHome = Optional.ofNullable(System.getenv("LANGUAGE_MODELS_HOME"))
                .map(Path::of)
                .orElse(LANGUAGE_MODELS_DEFAULT);

        if (!Files.isDirectory(languageModelsHome)) {
            throw new IllegalStateException("Could not find $LANGUAGE_MODELS_HOME, see doc/language-models.md");
        }
        return languageModelsHome;
    }
    private static LanguageModels getLanguageModels() {

        var languageModelsHome = getLanguageModelsPath();

        return new LanguageModels(
                languageModelsHome.resolve("tfreq-new-algo3.bin"),
                languageModelsHome.resolve("opennlp-sentence.bin"),
                languageModelsHome.resolve("English.RDR"),
                languageModelsHome.resolve("English.DICT"),
                languageModelsHome.resolve("lid.176.ftz"),
                languageModelsHome.resolve("segments.bin")
        );
    }
}
