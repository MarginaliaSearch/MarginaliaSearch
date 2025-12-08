package nu.marginalia.api.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

@Singleton
public class QueryClient  {

    private static final Logger log = LoggerFactory.getLogger(QueryClient.class);
    private final ExecutorService virtualThreadService = Executors.newVirtualThreadPerTaskExecutor();

    private static final Summary wmsa_qs_api_search_time = Summary.build()
            .name("wmsa_qs_api_search_time")
            .help("query service search time")
            .register();

    private final GrpcSingleNodeChannelPool<QueryApiGrpc.QueryApiBlockingStub> queryApiPool;

    @Inject
    public QueryClient(GrpcChannelPoolFactoryIf channelPoolFactory) throws InterruptedException {
        this.queryApiPool = channelPoolFactory.createSingle(
                ServiceKey.forGrpcApi(QueryApiGrpc.class, ServicePartition.any()),
                QueryApiGrpc::newBlockingStub);

        // Hold up initialization until we have a downstream connection
        this.queryApiPool.awaitChannel(Duration.ofSeconds(5));
    }

    @CheckReturnValue
    public QueryResponse search(QueryFilterSpec filterSpec,
                                String humanQuery,
                                String languageIsoCode,
                                NsfwFilterTier filterTier,
                                RpcQueryLimits limits,
                                int page) throws TimeoutException
    {
        RpcQsQuery.Builder queryBuilder = RpcQsQuery.newBuilder();

        filterSpec.configure(queryBuilder);

        var query = queryBuilder.setHumanQuery(humanQuery)
                .setLangIsoCode(languageIsoCode)
                .setNsfwFilterTierValue(filterTier.getCodedValue())
                .setQueryLimits(limits)
                .setPagination(
                        RpcQsQueryPagination.newBuilder()
                                .setPage(page)
                                .setPageSize(Math.min(100, limits.getResultsTotal()))
                                .build()
                )
                .build();

        try (var _ = wmsa_qs_api_search_time.startTimer()) {
            return queryApiPool.call(QueryApiGrpc.QueryApiBlockingStub::query)
                    .async(virtualThreadService)
                    .run(query)
                    .thenApply(QueryProtobufCodec::convertQueryResponse)
                    .get(limits.getTimeoutMs() * 2L, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException|ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean invalidateFilterCache(String userId, String filterId) {
        List<Future<Empty>> ret = queryApiPool.call(QueryApiGrpc.QueryApiBlockingStub::invalidateFilterCache)
                .broadcast()
                .run(
                    RpcQsInvalidateFilter.newBuilder()
                        .setUserId(userId)
                        .setFilterId(filterId)
                        .build()
                );

        for (var fut : ret) {
            if (fut.state() != Future.State.SUCCESS)
                return false;
        }

        return true;

    }

}
