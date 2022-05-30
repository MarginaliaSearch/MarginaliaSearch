package nu.marginalia.wmsa.edge.search;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.api.model.ApiSearchResult;
import nu.marginalia.wmsa.api.model.ApiSearchResults;
import nu.marginalia.wmsa.client.exception.TimeoutException;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryResponse;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.siteinfo.DomainInformationService;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class EdgeSearchService extends Service {

    private final EdgeDataStoreDao edgeDataStoreDao;
    private final EdgeIndexClient indexClient;
    private final AssistantClient assistantClient;
    private final UnitConversion unitConversion;
    private final EdgeSearchOperator searchOperator;
    private final EdgeDomainBlacklist blacklist;
    private final ScreenshotService screenshotService;
    private DomainInformationService domainInformationService;

    private final MustacheRenderer<BrowseResultSet> browseResultsRenderer;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRenderer;
    private final MustacheRenderer<DecoratedSearchResults> searchResultsRendererGmi;
    private final MustacheRenderer<DictionaryResponse> dictionaryRenderer;
    private final MustacheRenderer<DictionaryResponse> dictionaryRendererGmi;
    private final MustacheRenderer<Map<String, String>> conversionRenderer;
    private final MustacheRenderer<Map<String, String>> conversionRendererGmi;

    private final MustacheRenderer<DomainInformation> siteInfoRenderer;
    private final MustacheRenderer<DomainInformation> siteInfoRendererGmi;

    private final Gson gson = new GsonBuilder().create();

    private static final Logger logger = LoggerFactory.getLogger(EdgeSearchService.class);
    private final int indexSize = 0;

    private final String maintenanceMessage = null;

    @SneakyThrows
    @Inject
    public EdgeSearchService(@Named("service-host") String ip,
                             @Named("service-port") Integer port,
                             EdgeDataStoreDao edgeDataStoreDao,
                             EdgeIndexClient indexClient,
                             RendererFactory rendererFactory,
                             Initialization initialization,
                             MetricsServer metricsServer,
                             AssistantClient assistantClient,
                             UnitConversion unitConversion,
                             EdgeSearchOperator searchOperator,
                             EdgeDomainBlacklist blacklist,
                             ScreenshotService screenshotService,
                             DomainInformationService domainInformationService
                             ) {
        super(ip, port, initialization, metricsServer);
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.indexClient = indexClient;

        browseResultsRenderer = rendererFactory.renderer("edge/browse-results");

        searchResultsRenderer = rendererFactory.renderer("edge/search-results");
        searchResultsRendererGmi = rendererFactory.renderer("edge/search-results-gmi");

        dictionaryRenderer = rendererFactory.renderer("edge/dictionary-results");
        dictionaryRendererGmi = rendererFactory.renderer("edge/dictionary-results-gmi");

        siteInfoRenderer = rendererFactory.renderer("edge/site-info");
        siteInfoRendererGmi = rendererFactory.renderer("edge/site-info-gmi");

        conversionRenderer = rendererFactory.renderer("edge/conversion-results");
        conversionRendererGmi  = rendererFactory.renderer("edge/conversion-results-gmi");

        this.assistantClient = assistantClient;
        this.unitConversion = unitConversion;
        this.searchOperator = searchOperator;
        this.blacklist = blacklist;
        this.screenshotService = screenshotService;
        this.domainInformationService = domainInformationService;

        Spark.staticFiles.expireTime(600);

        Spark.get("/search", this::pathSearch);

        Spark.get("/api/search", this::apiSearch, gson::toJson);
        Spark.get("/public/search", this::pathSearch);
        Spark.get("/site-search/:site/*", this::siteSearchRedir);
        Spark.get("/public/site-search/:site/*", this::siteSearchRedir);

        Spark.exception(Exception.class, (e,p,q) -> {
            logger.error("Error during processing", e);
            serveError(Context.fromRequest(p), q);
        });

        Spark.awaitInitialization();
    }

    private Object siteSearchRedir(Request request, Response response) {
        final String site = request.params("site");
        final String queryRaw = request.splat()[0];

        final String query = URLEncoder.encode(String.format("%s site:%s", queryRaw, site), StandardCharsets.UTF_8);
        final String profile = request.queryParamOrDefault("profile", "yolo");

        response.redirect("https://search.marginalia.nu/search?query="+query+"&profile="+profile);

        return null;
    }


    private void serveError(Context ctx, Response rsp) {
        boolean isIndexUp = indexClient.isAlive();

        try {
            if (!isIndexUp) {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">offline</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
            } else if (indexClient.isBlocked(ctx).blockingFirst()) {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">starting up</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
            }
            else {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"></head><body><article><h1>Error</h1><p>Oops! An unknown error occurred. The index server seems to be up, so I don't know why this is. Please send an email to kontakt@marginalia.nu telling me what you did :-) </p></body></html>");
            }
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">unresponsive</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
        }

    }

    @SneakyThrows
    private Object apiSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);
        final String queryParam = request.queryParams("query");
        final int limit;
        EdgeSearchProfile profile = EdgeSearchProfile.YOLO;

        String count = request.queryParamOrDefault("count", "20");
        limit = Integer.parseInt(count);

        String index = request.queryParamOrDefault("index", "0");
        if (!Strings.isNullOrEmpty(index)) {
            profile = switch (index) {
                case "0" -> EdgeSearchProfile.YOLO;
                case "1" -> EdgeSearchProfile.MODERN;
                case "2" -> EdgeSearchProfile.DEFAULT;
                case "3" -> EdgeSearchProfile.CORPO_CLEAN;
                default -> EdgeSearchProfile.CORPO_CLEAN;
            };
        }

        final String humanQuery = queryParam.trim();

        var results = searchOperator.doApiSearch(ctx, new EdgeUserSearchParameters(humanQuery, profile, ""));

        return new ApiSearchResults("RESTRICTED", humanQuery, results.stream().map(ApiSearchResult::new).limit(limit).collect(Collectors.toList()));
    }

    @SneakyThrows
    private Object pathSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);

        final String queryParam = request.queryParams("query");
        if (null == queryParam || queryParam.isBlank()) {
            response.redirect("https://search.marginalia.nu/");
            return null;
        }

        final String profileStr = Optional.ofNullable(request.queryParams("profile")).orElse("yolo");

        try {
            final String humanQuery = queryParam.trim();
            final String format = request.queryParams("format");

            var eval = unitConversion.tryEval(ctx, humanQuery);
            var conversion = unitConversion.tryConversion(ctx, humanQuery);
            if (conversion.isPresent()) {
                if ("gmi".equals(format)) {
                    response.type("text/gemini");
                    return conversionRendererGmi.render(Map.of("query", humanQuery, "result", conversion.get()));
                } else {
                    return conversionRenderer.render(Map.of("query", humanQuery, "result", conversion.get(), "profile", profileStr));
                }
            }
            if (humanQuery.matches("define:[A-Za-z\\s-0-9]+")) {
                var results = lookupDefinition(ctx, humanQuery);

                if ("gmi".equals(format)) {
                    response.type("text/gemini");
                    return dictionaryRendererGmi.render(results, Map.of("query", humanQuery));
                } else {
                    return dictionaryRenderer.render(results, Map.of("query", humanQuery, "profile", profileStr));
                }
            } else if (humanQuery.matches("site:[.A-Za-z\\-0-9]+")) {
                var results = siteInfo(ctx, humanQuery);


                var domain = results.getDomain();
                logger.info("Domain: {}", domain);

                DecoratedSearchResultSet resultSet;
                Path screenshotPath = null;
                if (null != domain) {
                    resultSet = searchOperator.performDumbQuery(ctx, EdgeSearchProfile.CORPO, IndexBlock.Words, 100, 100, "site:"+domain);

                    screenshotPath = Path.of("/screenshot/" + edgeDataStoreDao.getDomainId(domain).getId());
                }
                else {
                    resultSet = new DecoratedSearchResultSet(Collections.emptyList());
                }

                if ("gmi".equals(format)) {
                    response.type("text/gemini");
                    return siteInfoRendererGmi.render(results, Map.of("query", humanQuery));
                } else {
                    return siteInfoRenderer.render(results, Map.of("query", humanQuery, "focusDomain", Objects.requireNonNullElse(domain, ""), "profile", profileStr, "results", resultSet.resultSet, "screenshot", screenshotPath == null ? "" : screenshotPath.toString()));
                }
            } else if (humanQuery.matches("browse:[.A-Za-z\\-0-9]+")) {
                var results = browseSite(ctx, humanQuery);

                if (null != results) {
                    return browseResultsRenderer.render(results, Map.of("query", humanQuery, "profile", profileStr));
                }
            }


            final var jsSetting = Optional.ofNullable(request.queryParams("js")).orElse("default");
            var results = searchOperator.doSearch(ctx, new EdgeUserSearchParameters(humanQuery,
                    EdgeSearchProfile.getSearchProfile(profileStr), jsSetting), eval.orElse(null)
            );

            results.getResults().removeIf(detail -> blacklist.isBlacklisted(edgeDataStoreDao.getDomainId(detail.url.domain)));

            if ("gmi".equals(format)) {
                response.type("text/gemini");
                return searchResultsRendererGmi.render(results);
            } else {
                if (maintenanceMessage != null) {
                    return searchResultsRenderer.render(results, Map.of("maintenanceMessage", maintenanceMessage));
                }
                else {
                    return searchResultsRenderer.render(results);
                }
            }
        }
        catch (TimeoutException te) {
            serveError(ctx, response);
            return null;
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            serveError(ctx, response);
            return null;
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

    private BrowseResultSet browseSite(Context ctx, String humanQuery) {
        String definePrefix = "browse:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        try {
            if ("random".equals(word)) {
                var results = edgeDataStoreDao.getRandomDomains(25, blacklist);
                results.removeIf(res -> !screenshotService.hasScreenshot(new EdgeId<>(res.domainId)));
                return new BrowseResultSet(results);
            }
            else {
                var domain = edgeDataStoreDao.getDomainId(new EdgeDomain(word));
                var neighbors = edgeDataStoreDao.getDomainNeighborsAdjacent(domain, blacklist, 45);

                neighbors.removeIf(res -> !screenshotService.hasScreenshot(new EdgeId<>(res.domainId)));

                return new BrowseResultSet(neighbors);
            }
        }
        catch (Exception ex) {
            logger.info("No Results");
            return null;
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
