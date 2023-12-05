
package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.assistant.client.AssistantClient;
import nu.marginalia.assistant.client.model.DictionaryResponse;
import nu.marginalia.client.Context;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DefinitionCommand implements SearchCommandInterface {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MustacheRenderer<DictionaryResponse> dictionaryRenderer;
    private final AssistantClient assistantClient;


    private final Predicate<String> queryPatternPredicate = Pattern.compile("^define:[A-Za-z\\s-0-9]+$").asPredicate();

    @Inject
    public DefinitionCommand(RendererFactory rendererFactory, AssistantClient assistantClient)
            throws IOException
    {

        dictionaryRenderer = rendererFactory.renderer("search/dictionary-results");
        this.assistantClient = assistantClient;
    }

    @Override
    public Optional<Object> process(Context ctx, Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        var results = lookupDefinition(ctx, parameters.query());

        return Optional.of(dictionaryRenderer.render(results,
                Map.of("query", parameters.query(),
                        "profile", parameters.profileStr())
        ));
    }


    @SneakyThrows
    private DictionaryResponse lookupDefinition(Context ctx, String humanQuery) {
        String definePrefix = "define:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        logger.info("Defining: {}", word);
        var results = assistantClient
                .dictionaryLookup(ctx, word)
                .blockingFirst();
        logger.debug("Results = {}", results);

        return results;
    }
}
