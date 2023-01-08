package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.model.DomainInformation;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.siteinfo.DomainInformationService;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchQueryIndexService;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SiteListCommand implements SearchCommandInterface {
    private final EdgeDataStoreDao dataStoreDao;
    private final DomainInformationService domainInformationService;
    private final EdgeSearchQueryIndexService searchQueryIndexService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MustacheRenderer<DomainInformation> siteInfoRenderer;

    private final Predicate<String> queryPatternPredicate = Pattern.compile("^site:[.A-Za-z\\-0-9]+$").asPredicate();

    @Inject
    public SiteListCommand(
            DomainInformationService domainInformationService,
            EdgeDataStoreDao dataStoreDao,
            RendererFactory rendererFactory,
            EdgeSearchQueryIndexService searchQueryIndexService)
            throws IOException
    {
        this.dataStoreDao = dataStoreDao;
        this.domainInformationService = domainInformationService;

        siteInfoRenderer = rendererFactory.renderer("edge/site-info");
        this.searchQueryIndexService = searchQueryIndexService;
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        if (!queryPatternPredicate.test(query)) {
            return Optional.empty();
        }

        var results = siteInfo(ctx, query);
        var domain = results.getDomain();

        List<EdgeUrlDetails> resultSet;
        Path screenshotPath = null;
        Integer domainId = -1;
        if (null != domain) {
            resultSet = searchQueryIndexService.performDumbQuery(ctx, EdgeSearchProfile.CORPO, 100, 100, "site:"+domain);
            domainId = dataStoreDao.getDomainId(domain).id();
            screenshotPath = Path.of("/screenshot/" + domainId);
        }
        else {
            resultSet = Collections.emptyList();
        }

        Map<String, Object> renderObject = new HashMap<>(10);

        renderObject.put("query", query);
        renderObject.put("hideRanking", true);
        renderObject.put("profile", parameters.profileStr());
        renderObject.put("results", resultSet);
        renderObject.put("screenshot", screenshotPath == null ? "" : screenshotPath.toString());
        renderObject.put("domainId", domainId);
        renderObject.put("focusDomain", domain);

        return Optional.of(siteInfoRenderer.render(results, renderObject));
    }


    private DomainInformation siteInfo(Context ctx, String humanQuery) {
        String definePrefix = "site:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        logger.info("Fetching Site Info: {}", word);
        var results = domainInformationService.domainInfo(word).orElseGet(DomainInformation::new);

        logger.debug("Results = {}", results);

        return results;

    }

}
