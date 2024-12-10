
package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.math.model.DictionaryResponse;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.NavbarModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DefinitionCommand implements SearchCommandInterface {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MathClient mathClient;
    private final JteRenderer renderer;


    private final Predicate<String> queryPatternPredicate = Pattern.compile("^define:[A-Za-z\\s-0-9]+$").asPredicate();

    @Inject
    public DefinitionCommand(MathClient mathClient, JteRenderer renderer) {

        this.mathClient = mathClient;
        this.renderer = renderer;
    }

    @Override
    public Optional<Object> process(Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        DictionaryResponse result = lookupDefinition(parameters.query());

        return Optional.of(renderer.render("serp/dict-lookup.jte",
                Map.of("parameters", parameters,
                        "result", result,
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
