package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.SmallSearchSet;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class IndexGrpcService
        extends IndexApiGrpc.IndexApiImplBase
        implements DiscoverableService
{

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, KeywordHasher> keywordHasherByLangIso;

    // This marker is used to mark sensitive log messages that are related to queries
    // so that they can be filtered out in the production logging configuration
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Counter wmsa_query_timeouts = Counter.build()
            .name("wmsa_index_query_timeouts")
            .help("Query timeout counter")
            .labelNames("node", "api")
            .register();
    private static final Histogram wmsa_query_time = Histogram.build()
            .name("wmsa_index_query_time")
            .linearBuckets(0.05, 0.05, 15)
            .labelNames("node", "api")
            .help("Index-side query time")
            .register();

    private final StatefulIndex statefulIndex;
    private final SearchSetsService searchSetsService;

    private final IndexResultRankingService rankingService;
    private final String nodeName;
    private final int nodeId;

    @Inject
    public IndexGrpcService(ServiceConfiguration serviceConfiguration,
                            LanguageConfiguration languageConfiguration,
                            StatefulIndex statefulIndex,
                            SearchSetsService searchSetsService,
                            IndexResultRankingService rankingService)
    {
        this.nodeId = serviceConfiguration.node();
        this.nodeName = Integer.toString(nodeId);
        this.statefulIndex = statefulIndex;
        this.searchSetsService = searchSetsService;
        this.rankingService = rankingService;
        this.keywordHasherByLangIso = new HashMap<>();

        for (LanguageDefinition definition : languageConfiguration.languages()) {
            keywordHasherByLangIso.put(definition.isoCode(), definition.keywordHasher());
        }
    }

    // GRPC endpoint

    public void query(RpcIndexQuery request,
                      StreamObserver<RpcDecoratedResultItem> responseObserver) {

        try {
            long endTime = System.currentTimeMillis() + request.getQueryLimits().getTimeoutMs();
            KeywordHasher hasher = findHasher(request);

            List<RpcDecoratedResultItem> results = wmsa_query_time
                    .labels(nodeName, "GRPC")
                    .time(() -> {
                        // Perform the search
                        try {

                            if (!statefulIndex.isLoaded()) {
                                // Short-circuit if the index is not loaded, as we trivially know that there can be no results
                                return List.of();
                            }
                            var rankingContext = SearchContext.create(statefulIndex.get(), hasher, request, getSearchSet(request));
                            return new IndexQueryExecution(rankingContext, nodeId, rankingService, statefulIndex.get()).run();
                        }
                        catch (Exception ex) {
                            logger.error("Error in handling request", ex);
                            return List.of();
                        }
                    });

            if (System.currentTimeMillis() >= endTime) {
                wmsa_query_timeouts
                        .labels(nodeName, "GRPC")
                        .inc();
            }

            // Send the results back to the client
            for (var result : results) {
                responseObserver.onNext(result);
            }

            responseObserver.onCompleted();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            responseObserver.onError(Status.INTERNAL.withCause(ex).asRuntimeException());
        }
    }

    private KeywordHasher findHasher(RpcIndexQuery request) {
        KeywordHasher hasher = keywordHasherByLangIso.get(request.getLangIsoCode());
        if (hasher != null)
            return hasher;

        hasher = keywordHasherByLangIso.get("en");
        if (hasher != null)
            return hasher;

        throw new IllegalStateException("Could not find fallback keyword hasher for iso code 'en'");
    }


    // exists for test access
    public List<RpcDecoratedResultItem> justQuery(SearchSpecification specsSet) {
        try {
            if (!statefulIndex.isLoaded()) {
                // Short-circuit if the index is not loaded, as we trivially know that there can be no results
                return List.of();
            }

            var currentIndex = statefulIndex.get();

            return new IndexQueryExecution(SearchContext.create(currentIndex, keywordHasherByLangIso.get("en"), specsSet, getSearchSet(specsSet)), 1, rankingService, statefulIndex.get()).run();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            return List.of();
        }
    }

    private SearchSet getSearchSet(SearchSpecification specsSet) {

        if (specsSet.domains != null && !specsSet.domains.isEmpty()) {
            return new SmallSearchSet(specsSet.domains);
        }

        return searchSetsService.getSearchSetByName(specsSet.searchSetIdentifier);
    }

    private SearchSet getSearchSet(RpcIndexQuery request) {

        if (request.getDomainsCount() > 0) {
            return new SmallSearchSet(request.getDomainsList());
        }

        return searchSetsService.getSearchSetByName(request.getSearchSetIdentifier());
    }


}

