package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultSet;
import nu.marginalia.index.index.IndexQueryService;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.IndexSearchParameters;
import nu.marginalia.index.results.IndexResultValuatorService;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.SmallSearchSet;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.sql.SQLException;
import java.util.*;

@Singleton
public class IndexQueryGrpcService extends IndexApiGrpc.IndexApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // This marker is used to mark sensitive log messages that are related to queries
    // so that they can be filtered out in the production logging configuration
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Counter wmsa_query_timeouts = Counter.build()
            .name("wmsa_index_query_timeouts")
            .help("Query timeout counter")
            .labelNames("node", "api")
            .register();
    private static final Gauge wmsa_query_cost = Gauge.build()
            .name("wmsa_index_query_cost")
            .help("Computational cost of query")
            .labelNames("node", "api")
            .register();
    private static final Histogram wmsa_query_time = Histogram.build()
            .name("wmsa_index_query_time")
            .linearBuckets(0.05, 0.05, 15)
            .labelNames("node", "api")
            .help("Index-side query time")
            .register();


    private final StatefulIndex index;
    private final SearchSetsService searchSetsService;

    private final IndexQueryService indexQueryService;
    private final IndexResultValuatorService resultValuator;

    private final int nodeId;


    @Inject
    public IndexQueryGrpcService(ServiceConfiguration serviceConfiguration,
                                 StatefulIndex index,
                                 SearchSetsService searchSetsService,
                                 IndexQueryService indexQueryService,
                                 IndexResultValuatorService resultValuator)
    {
        this.nodeId = serviceConfiguration.node();
        this.index = index;
        this.searchSetsService = searchSetsService;
        this.resultValuator = resultValuator;
        this.indexQueryService = indexQueryService;
    }

    // GRPC endpoint
    @SneakyThrows
    public void query(RpcIndexQuery request,
                      StreamObserver<RpcDecoratedResultItem> responseObserver) {

        try {
            var params = new IndexSearchParameters(request, getSearchSet(request));

            final String nodeName = Integer.toString(nodeId);

            SearchResultSet results = wmsa_query_time
                    .labels(nodeName, "GRPC")
                    .time(() -> executeSearch(params));

            wmsa_query_cost
                    .labels(nodeName, "GRPC")
                    .set(params.getDataCost());

            if (!params.hasTimeLeft()) {
                wmsa_query_timeouts
                        .labels(nodeName, "GRPC")
                        .inc();
            }

            for (var result : results.results) {

                var rawResult = result.rawIndexResult;

                var rawItem = RpcRawResultItem.newBuilder();
                rawItem.setCombinedId(rawResult.combinedId);
                rawItem.setResultsFromDomain(rawResult.resultsFromDomain);

                for (var score : rawResult.keywordScores) {
                    rawItem.addKeywordScores(
                            RpcResultKeywordScore.newBuilder()
                                    .setEncodedDocMetadata(score.encodedDocMetadata())
                                    .setEncodedWordMetadata(score.encodedWordMetadata())
                                    .setKeyword(score.keyword)
                                    .setHtmlFeatures(score.htmlFeatures())
                                    .setHasPriorityTerms(score.hasPriorityTerms())
                                    .setSubquery(score.subquery)
                    );
                }

                var decoratedBuilder = RpcDecoratedResultItem.newBuilder()
                        .setDataHash(result.dataHash)
                        .setDescription(result.description)
                        .setFeatures(result.features)
                        .setFormat(result.format)
                        .setRankingScore(result.rankingScore)
                        .setTitle(result.title)
                        .setUrl(result.url.toString())
                        .setWordsTotal(result.wordsTotal)
                        .setRawItem(rawItem);

                if (result.pubYear != null) {
                    decoratedBuilder.setPubYear(result.pubYear);
                }
                responseObserver.onNext(decoratedBuilder.build());
            }

            responseObserver.onCompleted();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            responseObserver.onError(ex);
        }
    }

    // exists for test access
    @SneakyThrows
    SearchResultSet justQuery(SearchSpecification specsSet) {
        return executeSearch(new IndexSearchParameters(specsSet, getSearchSet(specsSet)));
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
    private SearchResultSet executeSearch(IndexSearchParameters params) throws SQLException {

        if (!index.isLoaded()) {
            // Short-circuit if the index is not loaded, as we trivially know that there can be no results
            return new SearchResultSet(List.of());
        }

        var rankingContext = createRankingContext(params.rankingParams, params.subqueries);

        logger.info(queryMarker, "{}", params.queryParams);

        var resultIds = indexQueryService.evaluateSubqueries(params);
        var resultItems = resultValuator.findBestResults(params,
                rankingContext,
                resultIds);

        return new SearchResultSet(resultItems);
    }

    private ResultRankingContext createRankingContext(ResultRankingParameters rankingParams, List<SearchSubquery> subqueries) {
        final var termToId = SearchTermsUtil.getAllIncludeTerms(subqueries);
        final Map<String, Integer> termFrequencies = new HashMap<>(termToId.size());
        final Map<String, Integer> prioFrequencies = new HashMap<>(termToId.size());

        termToId.forEach((key, id) -> termFrequencies.put(key, index.getTermFrequency(id)));
        termToId.forEach((key, id) -> prioFrequencies.put(key, index.getTermFrequencyPrio(id)));

        return new ResultRankingContext(index.getTotalDocCount(),
                rankingParams,
                termFrequencies,
                prioFrequencies);
    }

}

