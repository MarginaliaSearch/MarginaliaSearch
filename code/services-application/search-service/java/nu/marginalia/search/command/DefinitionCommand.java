
package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.math.model.DictionaryResponse;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DefinitionCommand implements SearchCommandInterface {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MathClient mathClient;
    private final LanguageConfiguration languageConfiguration;
    private final JteRenderer renderer;


    private final Predicate<String> queryPatternPredicate = Pattern.compile("^define:[A-Za-z\\s-0-9]+$").asPredicate();

    @Inject
    public DefinitionCommand(MathClient mathClient, LanguageConfiguration languageConfiguration, JteRenderer renderer) {

        this.mathClient = mathClient;
        this.languageConfiguration = languageConfiguration;
        this.renderer = renderer;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        DictionaryResponse result = lookupDefinition(parameters.query());

        return Optional.of(new MapModelAndView("serp/dict-lookup.jte",
                Map.of("parameters", parameters,
                        "result", result,
                        "languageDefinitions", languageConfiguration.languages(),
                        "navbar", NavbarModel.SEARCH)
        ));
    }


    private DictionaryResponse lookupDefinition(String humanQuery) {
        String definePrefix = "define:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        try {
            return mathClient
                    .dictionaryLookup(word)
                    .get(250, TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            logger.error("Failed to lookup definition for word: " + word, e);

            throw new RuntimeException(e);
        }
    }
}
