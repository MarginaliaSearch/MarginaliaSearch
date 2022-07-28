package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;

public class SearchCommand implements SearchCommandInterface {
    private final EdgeDomainBlacklist blacklist;
    private final EdgeDataStoreDao dataStoreDao;
    private final EdgeSearchOperator searchOperator;
    private final UnitConversion unitConversion;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;

    @Inject
    public SearchCommand(EdgeDomainBlacklist blacklist,
                         EdgeDataStoreDao dataStoreDao,
                         EdgeSearchOperator searchOperator,
                         UnitConversion unitConversion,
                         RendererFactory rendererFactory) throws IOException {
        this.blacklist = blacklist;
        this.dataStoreDao = dataStoreDao;
        this.searchOperator = searchOperator;
        this.unitConversion = unitConversion;

        searchResultsRenderer = rendererFactory.renderer("edge/search-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        @CheckForNull Future<String> eval = unitConversion.tryEval(ctx, query);

        EdgeUserSearchParameters params = new EdgeUserSearchParameters(query, parameters.profile(), parameters.js());
        DecoratedSearchResults results = searchOperator.doSearch(ctx, params, eval);

        results.getResults().removeIf(detail -> blacklist.isBlacklisted(dataStoreDao.getDomainId(detail.url.domain)));

        return Optional.of(searchResultsRenderer.render(results));
    }
}
