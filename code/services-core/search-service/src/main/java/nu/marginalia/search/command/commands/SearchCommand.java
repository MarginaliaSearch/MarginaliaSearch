package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.model.dbcommon.DomainBlacklist;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.query.model.UserSearchParameters;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;

import java.io.IOException;
import java.util.Optional;

public class SearchCommand implements SearchCommandInterface {
    private final DomainBlacklist blacklist;
    private final SearchOperator searchOperator;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;


    @Inject
    public SearchCommand(DomainBlacklist blacklist,
                         SearchOperator searchOperator,
                         RendererFactory rendererFactory
                         ) throws IOException {
        this.blacklist = blacklist;
        this.searchOperator = searchOperator;

        searchResultsRenderer = rendererFactory.renderer("search/search-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        UserSearchParameters params = new UserSearchParameters(query, parameters.profile(), parameters.js());

        DecoratedSearchResults results = searchOperator.doSearch(ctx, params);

        results.results.removeIf(this::isBlacklisted);

        return Optional.of(searchResultsRenderer.render(results));
    }

    private boolean isBlacklisted(UrlDetails details) {
        return blacklist.isBlacklisted(details.domainId);
    }
}
