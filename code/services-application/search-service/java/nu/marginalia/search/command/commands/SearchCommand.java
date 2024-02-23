package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Response;

import java.io.IOException;
import java.util.Optional;

public class SearchCommand implements SearchCommandInterface {
    private final SearchOperator searchOperator;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;


    @Inject
    public SearchCommand(SearchOperator searchOperator,
                         RendererFactory rendererFactory) throws IOException {
        this.searchOperator = searchOperator;

        searchResultsRenderer = rendererFactory.renderer("search/search-results");
    }

    @Override
    public Optional<Object> process(Response response, SearchParameters parameters) {
        DecoratedSearchResults results = searchOperator.doSearch(parameters);

        return Optional.of(searchResultsRenderer.render(results));
    }
}
