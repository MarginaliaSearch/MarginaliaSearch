package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;

public class SearchCommand implements SearchCommandInterface {
    private EdgeDomainBlacklist blacklist;
    private EdgeDataStoreDao dataStoreDao;
    private EdgeSearchOperator searchOperator;
    private UnitConversion unitConversion;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRendererGmi;

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
        searchResultsRendererGmi = rendererFactory.renderer("edge/search-results-gmi");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        @CheckForNull Future<String> eval = unitConversion.tryEval(ctx, query);

        var results = searchOperator.doSearch(ctx, new EdgeUserSearchParameters(query,
                parameters.profile(), parameters.js()), eval
        );

        results.getResults().removeIf(detail -> blacklist.isBlacklisted(dataStoreDao.getDomainId(detail.url.domain)));

        if (parameters.responseType() == ResponseType.GEMINI) {
            return Optional.of(searchResultsRendererGmi.render(results));
        } else {
            return Optional.of(searchResultsRenderer.render(results));
        }
    }
}
