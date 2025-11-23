package nu.marginalia.functions.searchquery;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Histogram;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterCache;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

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

    private Optional<CompiledSearchFilterSpec> getFilter(RpcQsQuery request) {
        final CompiledSearchFilterSpec identifierFilter;
        final CompiledSearchFilterSpec adHocFilter;

        // A filter identifier is provided
        if (request.hasFilterIdentifier()) {
            try {
                var identifier = request.getFilterIdentifier();

                identifierFilter = searchFilterCache.get(
                        identifier.getUserId(),
                        identifier.getIdentifier()
                );

            }
            catch (ExecutionException ex) {
                return Optional.empty();
            }
        }
        else {
            identifierFilter = null;
        }

        // An ad-hoc filter is provided
        if (request.hasFilterSpec()) {
            adHocFilter = new CompiledSearchFilterSpec(request.getFilterSpec());
        }
        else {
            adHocFilter = null;
        }

        if (identifierFilter != null && adHocFilter != null) {
            CompiledSearchFilterSpec combinedFilter =
                    CompiledSearchFilterSpec.merge(identifierFilter, adHocFilter);

            return Optional.of(combinedFilter);
        }
        else if (identifierFilter != null) return Optional.of(identifierFilter);
        else if (adHocFilter != null) return Optional.of(adHocFilter);

        // Neither a filter identifier, or an ad-hoc filter is provided
        try {
            return Optional.of(searchFilterCache.get(SearchFilterDefaults.SYSTEM_USER_ID, SearchFilterDefaults.SYSTEM_DEFAULT_FILTER));
        }
        catch (Exception ex) {
            throw new IllegalStateException("This should not happen (TM)", ex);
        }

    }

    Optional<ProcessedQuery> createQuery(RpcQsQuery request) {

        return getFilter(request)
                .map(compiledSearchFilterSpec ->
                        queryFactory.createQuery(
                                request,
                                compiledSearchFilterSpec,
                                null)
                );

    }

    public void query(RpcQsQuery request,
                            StreamObserver<RpcQsResponse> responseObserver) {
        try {
            wmsa_qs_query_time_grpc
                    .labels(Integer.toString(request.getQueryLimits().getTimeoutMs()),
                            Integer.toString(request.getQueryLimits().getResultsTotal()))
                    .time(() -> {

                        IndexClient.Pagination pagination = new IndexClient.Pagination(request.getPagination());

                        Optional<ProcessedQuery> maybeQuery = createQuery(request);
                        if (maybeQuery.isEmpty()) {
                            responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                            return;
                        }

                        ProcessedQuery query = maybeQuery.get();

                        // Execute the query on the index partitions
                        IndexClient.AggregateQueryResponse response = indexClient.executeQueries(query.indexQuery, pagination);

                        // Convert results to response and send it back
                        var responseBuilder = RpcQsResponse.newBuilder()
                                .addAllResults(response.results())
                                .setPagination(
                                        RpcQsResultPagination.newBuilder()
                                                .setPage(pagination.page())
                                                .setPageSize(pagination.pageSize())
                                                .setTotalResults(response.totalResults())
                                )
                                .setSpecs(query.indexQuery)
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
            RpcQueryLimits limits,
            String set,
            String langIsoCode,
            IndexClient.Pagination pagination,
            RpcResultRankingParameters rankingParameters) {

        var mockQsQuery = RpcQsQuery.newBuilder()
                .setQueryLimits(limits)
                .setLangIsoCode(langIsoCode)
                .setHumanQuery(originalQuery)
                .build();

        var query = queryFactory.createQuery(mockQsQuery,
                CompiledSearchFilterSpec
                        .builder("AD-HOC", "set:"+set)
                        .searchSetIdentifier(set)
                        .build(),
                rankingParameters);

        IndexClient.AggregateQueryResponse response
                = indexClient.executeQueries(query.indexQuery, pagination);

        return new DetailedDirectResult(query,
                Lists.transform(response.results(), QueryProtobufCodec::convertQueryResult),
                response.totalResults());
    }

    public void invalidateFilterCache(RpcQsInvalidateFilter request,
                                      StreamObserver<Empty> responseObserver) {

        searchFilterCache.invalidate(request.getUserId(), request.getFilterId());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
