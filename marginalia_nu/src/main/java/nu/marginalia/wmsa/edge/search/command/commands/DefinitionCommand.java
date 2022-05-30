
package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryResponse;
import nu.marginalia.wmsa.edge.search.command.ResponseType;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DefinitionCommand implements SearchCommandInterface {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MustacheRenderer<DictionaryResponse> dictionaryRenderer;
    private final MustacheRenderer<DictionaryResponse> dictionaryRendererGmi;
    private final AssistantClient assistantClient;


    private final Predicate<String> queryPatternPredicate = Pattern.compile("^define:[A-Za-z\\s-0-9]+$").asPredicate();

    @Inject
    public DefinitionCommand(RendererFactory rendererFactory, AssistantClient assistantClient)
            throws IOException
    {

        dictionaryRenderer = rendererFactory.renderer("edge/dictionary-results");
        dictionaryRendererGmi = rendererFactory.renderer("edge/dictionary-results-gmi");
        this.assistantClient = assistantClient;
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        if (!queryPatternPredicate.test(query.trim())) {
            return Optional.empty();
        }

        var results = lookupDefinition(ctx, query);

        if (parameters.responseType() == ResponseType.GEMINI) {
            return Optional.of(dictionaryRendererGmi.render(results, Map.of("query", parameters.profileStr())));
        } else {
            return Optional.of(dictionaryRenderer.render(results, Map.of("query", query, "profile", parameters.profileStr())));
        }
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
