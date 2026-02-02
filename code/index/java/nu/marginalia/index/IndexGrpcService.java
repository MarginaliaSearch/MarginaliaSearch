package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.RpcIndexQueryResponse;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.SmallSearchSet;
import nu.marginalia.index.searchset.connectivity.ConnectivitySets;
import nu.marginalia.index.searchset.connectivity.ConnectivityView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.linkdb.docs.DocumentDbReader;
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

    private static final Counter wmsa_query_timeouts = Counter.builder()
            .name("wmsa_index_query_timeouts")
            .help("Query timeout counter")
            .labelNames("node", "api")
            .register();
    private static final Histogram wmsa_query_time = Histogram.builder()
            .name("wmsa_index_query_time")
            .classicLinearUpperBounds(0.05, 0.05, 15)
            .labelNames("node", "api")
            .help("Index-side query time")
            .register();

    private final StatefulIndex statefulIndex;
    private final SearchSetsService searchSetsService;

    private final IndexResultRankingService rankingService;
    private final String nodeName;
    private final int nodeId;
    private final DocumentDbReader documentDbReader;
    private final ConnectivitySets connectivitySets;

    @Inject
    public IndexGrpcService(ServiceConfiguration serviceConfiguration,
                            LanguageConfiguration languageConfiguration,
                            StatefulIndex statefulIndex,
                            DocumentDbReader documentDbReader,
                            ConnectivitySets connectivitySets,
                            SearchSetsService searchSetsService,
                            IndexResultRankingService rankingService)
    {
        this.nodeId = serviceConfiguration.node();
        this.documentDbReader = documentDbReader;
        this.connectivitySets = connectivitySets;
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
                      StreamObserver<RpcIndexQueryResponse> responseObserver) {

        try {
            long endTime = System.currentTimeMillis() + request.getQueryLimits().getTimeoutMs();
            KeywordHasher hasher = findHasher(request);

            List<RpcDecoratedResultItem> results = wmsa_query_time
                    .labelValues(nodeName, "GRPC")
                    .time(() -> {
                        // Perform the search
                        try (StatefulIndex.IndexReference indexReference = statefulIndex.get()) {
                            if (!indexReference.isAvailable()) {
                                return List.of();
                            }

                            CombinedIndexReader index = indexReference.get();

                            final SearchSet set = getSearchSet(request);
                            final ConnectivityView connectivityView;

                            if (!set.imposesConstraint() && "en".equalsIgnoreCase(request.getLangIsoCode())) {
                                connectivityView = connectivitySets.getView();
                            }
                            else {
                                connectivityView = ConnectivityView.empty();
                            }

                            SearchContext rankingContext = SearchContext.create(index, hasher, request, set, connectivityView);

                            IndexQueryExecution queryExecution = new IndexQueryExecution(index, documentDbReader, rankingService, rankingContext, nodeId);

                            return queryExecution.run();
                        }
                        catch (IndexQueryExecution.TooManySimultaneousQueriesException ex) {
                            logger.error("Rejected request execution due to overload");
                            return List.of();
                        }
                        catch (Exception ex) {
                            logger.error("Error in handling request", ex);
                            return List.of();
                        }
                    });

            if (System.currentTimeMillis() >= endTime) {
                wmsa_query_timeouts
                        .labelValues(nodeName, "GRPC")
                        .inc();
            }

            responseObserver.onNext(RpcIndexQueryResponse.newBuilder()
                            .addAllResults(results)
                            .build());

            responseObserver.onCompleted();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            responseObserver.onError(Status.INTERNAL.withCause(ex).asRuntimeException());
        }
    }

    /** Keywords are translated to a numeric format via a 64 bit hash algorithm,
     * which varies depends on the language.
     */
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
    public List<RpcDecoratedResultItem> justQuery(RpcIndexQuery request) {
        try (var indexReference = statefulIndex.get()) {
            if (!indexReference.isAvailable())
                return List.of();

            CombinedIndexReader currentIndex = indexReference.get();

            SearchContext context = SearchContext.create(currentIndex,
                    keywordHasherByLangIso.get("en"), request, getSearchSet(request),
                    ConnectivityView.empty()
                    );

            return new IndexQueryExecution(currentIndex, documentDbReader, rankingService, context, 1).run();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            return List.of();
        }
    }

    private SearchSet getSearchSet(RpcIndexQuery request) {

        if (request.getRequiredDomainIdsCount() > 0) {
            return new SmallSearchSet(request.getRequiredDomainIdsList());
        }

        String identifier = request.getSearchSetIdentifier();
        return searchSetsService.getSearchSetByName(identifier);
    }


}

