package nu.marginalia.functions.searchquery;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Histogram;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.api.IndexClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class QueryGRPCService extends QueryApiGrpc.QueryApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private static final Histogram wmsa_qs_query_time_grpc = Histogram.build()
            .name("wmsa_qs_query_time_grpc")
            .labelNames("timeout", "count")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (GRPC endpoint)")
            .register();


    private final QueryFactory queryFactory;
    private final IndexClient indexClient;

    @Inject
    public QueryGRPCService(QueryFactory queryFactory,
                            IndexClient indexClient)
    {
        this.queryFactory = queryFactory;
        this.indexClient = indexClient;
    }

    /** GRPC endpoint that parses a query, delegates it to the index partitions, and then collects the results.
     */
    public void query(RpcQsQuery request, StreamObserver<RpcQsResponse> responseObserver)
    {
        try {
            wmsa_qs_query_time_grpc
                    .labels(Integer.toString(request.getQueryLimits().getTimeoutMs()),
                            Integer.toString(request.getQueryLimits().getResultsTotal()))
                    .time(() -> {
                var params = QueryProtobufCodec.convertRequest(request);
                var query = queryFactory.createQuery(params, ResultRankingParameters.sensibleDefaults());

                var indexRequest = QueryProtobufCodec.convertQuery(request, query);

                var requestPagination = request.getPagination();

                IndexClient.Pagination pagination = new IndexClient.Pagination(
                        requestPagination.getPage(),
                        requestPagination.getPageSize());

                // Execute the query on the index partitions
                IndexClient.AggregateQueryResponse response = indexClient.executeQueries(indexRequest, pagination);

                 // Convert results to response and send it back
                var responseBuilder = RpcQsResponse.newBuilder()
                        .addAllResults(response.results())
                        .setPagination(
                                RpcQsResultPagination.newBuilder()
                                        .setPage(response.page())
                                        .setTotalResults(response.totalResults())
                        )
                        .setSpecs(indexRequest)
                        .addAllSearchTermsHuman(query.searchTermsHuman);

                if (query.domain != null) {
                    responseBuilder.setDomain(query.domain);
                }

                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
            });
        } catch (Exception e) {
            logger.error("Exception", e);
            responseObserver.onError(e);
        }
    }

    public record DetailedDirectResult(ProcessedQuery processedQuery,
                                       List<DecoratedSearchResultItem> result,
                                       int totalResults) {}

    /** Local query execution, without GRPC. */
    public DetailedDirectResult executeDirect(
            String originalQuery,
            QueryParams params,
            IndexClient.Pagination pagination,
            ResultRankingParameters rankingParameters) {

        var query = queryFactory.createQuery(params, rankingParameters);
        IndexClient.AggregateQueryResponse response = indexClient.executeQueries(QueryProtobufCodec.convertQuery(originalQuery, query), pagination);

        return new DetailedDirectResult(query,
                Lists.transform(response.results(), QueryProtobufCodec::convertQueryResult),
                response.totalResults());
    }

}
