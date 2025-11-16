package nu.marginalia.functions.searchquery;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Histogram;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.searchfilter.SearchFilterCache;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

@Singleton
public class QueryGRPCService
        extends QueryApiGrpc.QueryApiImplBase
        implements DiscoverableService
{

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private static final Histogram wmsa_qs_query_time_grpc = Histogram.build()
            .name("wmsa_qs_query_time_grpc")
            .labelNames("timeout", "count")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (GRPC endpoint)")
            .register();


    private final QueryFactory queryFactory;
    private final NsfwDomainFilter nsfwDomainFilter;
    private final IndexClient indexClient;
    private final SearchFilterCache searchFilterCache;

    @Inject
    public QueryGRPCService(QueryFactory queryFactory,
                            NsfwDomainFilter nsfwDomainFilter,
                            IndexClient indexClient,
                            SearchFilterCache searchFilterCache)
    {
        this.queryFactory = queryFactory;
        this.nsfwDomainFilter = nsfwDomainFilter;
        this.indexClient = indexClient;
        this.searchFilterCache = searchFilterCache;
    }

    public void querySimple(RpcQsQuerySimple request,
                            io.grpc.stub.StreamObserver<RpcQsResponse> responseObserver) {
        try {
            wmsa_qs_query_time_grpc
                    .labels(Integer.toString(request.getQueryLimits().getTimeoutMs()),
                            Integer.toString(request.getQueryLimits().getResultsTotal()))
                    .time(() -> {

                        CompiledSearchFilterSpec filterSpec;
                        try {
                            filterSpec = searchFilterCache.get(
                                    request.getSearchFilterUser(),
                                    request.getSearchFilterIdentifier()
                            );
                        }
                        catch (ExecutionException ex) {
                            responseObserver.onError(Status.NOT_FOUND.withCause(ex).asRuntimeException());
                            return;
                        }

                        ProcessedQuery query = queryFactory.createQuery(request, filterSpec, PrototypeRankingParameters.sensibleDefaults());

                        RpcIndexQuery indexRequest = QueryProtobufCodec.convertQuery(request, query);
                        IndexClient.Pagination pagination = new IndexClient.Pagination(request.getPagination());

                        // Execute the query on the index partitions
                        IndexClient.AggregateQueryResponse response = indexClient.executeQueries(indexRequest, pagination);

                        // Convert results to response and send it back
                        var responseBuilder = RpcQsResponse.newBuilder()
                                .addAllResults(response.results())
                                .setPagination(
                                        RpcQsResultPagination.newBuilder()
                                                .setPage(pagination.page())
                                                .setPageSize(pagination.pageSize())
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
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
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

                ProcessedQuery query = queryFactory.createQuery(params, PrototypeRankingParameters.sensibleDefaults());

                RpcIndexQuery indexRequest = QueryProtobufCodec.convertQuery(request, query);
                IndexClient.Pagination pagination = new IndexClient.Pagination(request.getPagination());

                // Execute the query on the index partitions
                IndexClient.AggregateQueryResponse response = indexClient.executeQueries(indexRequest, pagination);

                 // Convert results to response and send it back
                var responseBuilder = RpcQsResponse.newBuilder()
                        .addAllResults(response.results())
                        .setPagination(
                                RpcQsResultPagination.newBuilder()
                                        .setPage(pagination.page())
                                        .setPageSize(pagination.pageSize())
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
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    public record DetailedDirectResult(ProcessedQuery processedQuery,
                                       List<DecoratedSearchResultItem> result,
                                       int totalResults) {

    }

    /** Local query execution, without GRPC. */
    public DetailedDirectResult executeDirect(
            String originalQuery,
            QueryParams params,
            IndexClient.Pagination pagination,
            RpcResultRankingParameters rankingParameters) {

        var query = queryFactory.createQuery(params, rankingParameters);
        IndexClient.AggregateQueryResponse response = indexClient.executeQueries(QueryProtobufCodec.convertQuery(originalQuery, query), pagination);

        return new DetailedDirectResult(query,
                Lists.transform(response.results(), QueryProtobufCodec::convertQueryResult),
                response.totalResults());
    }

}
