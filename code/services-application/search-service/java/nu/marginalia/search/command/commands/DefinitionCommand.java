
package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.math.model.DictionaryResponse;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.renderer.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DefinitionCommand implements SearchCommandInterface {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MustacheRenderer<DictionaryResponse> dictionaryRenderer;
    private final MathClient mathClient;


    private final Predicate<String> queryPatternPredicate = Pattern.compile("^define:[A-Za-z\\s-0-9]+$").asPredicate();

    @Inject
    public DefinitionCommand(RendererFactory rendererFactory, MathClient mathClient)
            throws IOException
    {

        dictionaryRenderer = rendererFactory.renderer("search/dictionary-results");
        this.mathClient = mathClient;
    }

    @Override
    public Optional<Object> process(Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        var results = lookupDefinition(parameters.query());

        return Optional.of(dictionaryRenderer.render(results,
                Map.of("query", parameters.query(),
                        "profile", parameters.profileStr())
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
