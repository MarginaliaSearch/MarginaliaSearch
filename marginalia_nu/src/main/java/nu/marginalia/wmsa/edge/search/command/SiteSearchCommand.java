package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResultSet;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.model.DomainInformation;
import nu.marginalia.wmsa.edge.search.siteinfo.DomainInformationService;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SiteSearchCommand implements SearchCommandInterface {
    private EdgeDomainBlacklist blacklist;
    private final EdgeDataStoreDao dataStoreDao;
    private final EdgeSearchOperator searchOperator;
    private DomainInformationService domainInformationService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MustacheRenderer<DomainInformation> siteInfoRenderer;
    private final MustacheRenderer<DomainInformation> siteInfoRendererGmi;

    private final Predicate<String> queryPatternPredicate = Pattern.compile("^site:[.A-Za-z\\-0-9]+$").asPredicate();
    @Inject
    public SiteSearchCommand(
            EdgeDomainBlacklist blacklist,
            EdgeDataStoreDao dataStoreDao,
            RendererFactory rendererFactory,
            EdgeSearchOperator searchOperator,
            DomainInformationService domainInformationService)
            throws IOException
    {
        this.blacklist = blacklist;
        this.dataStoreDao = dataStoreDao;

        siteInfoRenderer = rendererFactory.renderer("edge/site-info");
        siteInfoRendererGmi = rendererFactory.renderer("edge/site-info-gmi");

        this.searchOperator = searchOperator;
        this.domainInformationService = domainInformationService;
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        if (!queryPatternPredicate.test(query)) {
            return Optional.empty();
        }

        var results = siteInfo(ctx, query);

        var domain = results.getDomain();
        logger.info("Domain: {}", domain);

        DecoratedSearchResultSet resultSet;
        Path screenshotPath = null;
        if (null != domain) {
            resultSet = searchOperator.performDumbQuery(ctx, EdgeSearchProfile.CORPO, IndexBlock.Words, 100, 100, "site:"+domain);

            screenshotPath = Path.of("/screenshot/" + dataStoreDao.getDomainId(domain).getId());
        }
        else {
            resultSet = new DecoratedSearchResultSet(Collections.emptyList());
        }

        if (parameters.responseType() == ResponseType.GEMINI) {
            return Optional.of(siteInfoRendererGmi.render(results, Map.of("query", query)));
        } else {
            return Optional.of(siteInfoRenderer.render(results, Map.of("query", query, "focusDomain", Objects.requireNonNullElse(domain, ""), "profile", parameters.profileStr(), "results", resultSet.resultSet, "screenshot", screenshotPath == null ? "" : screenshotPath.toString())));
        }


    }


    private DomainInformation siteInfo(Context ctx, String humanQuery) {
        String definePrefix = "site:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        logger.info("Fetching Site Info: {}", word);
        var results = domainInformationService.domainInfo(word)
                .orElseGet(() -> new DomainInformation(null, false, 0, 0, 0, 0, 0, 0, 0, EdgeDomainIndexingState.UNKNOWN, Collections.emptyList()));

        logger.debug("Results = {}", results);

        return results;

    }

}
