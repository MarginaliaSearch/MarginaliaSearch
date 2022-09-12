package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.results.BrowseResultCleaner;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchUnitConversionService;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;

import java.io.IOException;
import java.util.Optional;

public class SearchCommand implements SearchCommandInterface {
    private final EdgeDomainBlacklist blacklist;
    private final EdgeDataStoreDao dataStoreDao;
    private final EdgeSearchOperator searchOperator;
    private final EdgeSearchUnitConversionService edgeSearchUnitConversionService;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;
    private BrowseResultCleaner browseResultCleaner;

    public static final int MAX_DOMAIN_RESULTS = 3;

    @Inject
    public SearchCommand(EdgeDomainBlacklist blacklist,
                         EdgeDataStoreDao dataStoreDao,
                         EdgeSearchOperator searchOperator,
                         EdgeSearchUnitConversionService edgeSearchUnitConversionService,
                         RendererFactory rendererFactory,
                         BrowseResultCleaner browseResultCleaner
                         ) throws IOException {
        this.blacklist = blacklist;
        this.dataStoreDao = dataStoreDao;
        this.searchOperator = searchOperator;
        this.edgeSearchUnitConversionService = edgeSearchUnitConversionService;
        this.browseResultCleaner = browseResultCleaner;

        searchResultsRenderer = rendererFactory.renderer("edge/search-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {

        EdgeUserSearchParameters params = new EdgeUserSearchParameters(query, parameters.profile(), parameters.js());
        DecoratedSearchResults results = searchOperator.doSearch(ctx, params);

        results.results.removeIf(detail -> blacklist.isBlacklisted(dataStoreDao.getDomainId(detail.url.domain)));

        results.domainResults.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        if (results.domainResults.size() > MAX_DOMAIN_RESULTS) {
            results.domainResults.subList(MAX_DOMAIN_RESULTS, results.domainResults.size()).clear();
        }

        return Optional.of(searchResultsRenderer.render(results));
    }
}
