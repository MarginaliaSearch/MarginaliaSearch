package nu.marginalia.index.api;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.service.NodeConfigurationWatcherIf;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final List<GrpcSingleNodeChannelPool<IndexApiGrpc.IndexApiFutureStub>> channelPools;
    private final DomainBlacklistImpl blacklist;
    private final NsfwDomainFilter nsfwDomainFilter;

    Counter wmsa_index_query_count = Counter.builder()
            .name("wmsa_nsfw_filter_result_count")
            .labelNames("tier")
            .help("Count of results filtered by NSFW tier")
            .register();


    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");
    private static final ExecutorService executor = useLoom ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newCachedThreadPool();

    @Inject
    public IndexClient(GrpcChannelPoolFactoryIf channelPoolFactory,
                       DomainBlacklistImpl blacklist,
                       NsfwDomainFilter nsfwDomainFilter,
                       NodeConfigurationWatcherIf nodeConfigurationWatcher
                       ) {
        channelPools = new ArrayList<>();

        for (int node: nodeConfigurationWatcher.getQueryNodes()) {
            channelPools.add(channelPoolFactory.createSingle(ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.partition(node)), IndexApiGrpc::newFutureStub));
        }

        this.blacklist = blacklist;
        this.nsfwDomainFilter = nsfwDomainFilter;
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);

    public record Pagination(int page, int pageSize) {
        public Pagination(RpcQsQueryPagination pagination) {
            this(pagination.getPage(), pagination.getPageSize());
        }

    }

    public record AggregateQueryResponse(List<RpcDecoratedResultItem> results,
                                         int page,
                                         int totalResults
                                     ) {}

    /** Execute a query on the index partitions and return the combined results. */
    public AggregateQueryResponse executeQueries(RpcIndexQuery indexRequest, Pagination pagination) {

        final int requestedMaxResults = indexRequest.getQueryLimits().getResultsTotal();
        int filterTier = indexRequest.getNsfwFilterTierValue();
        AtomicInteger totalNumResults = new AtomicInteger(0);

        Instant bailInstant  = Instant.now().plusMillis((int) (2 * indexRequest.getQueryLimits().getTimeoutMs()));

        List<RpcDecoratedResultItem> results = new ArrayList<>();
        List<ListenableFuture<RpcIndexQueryResponse>> futures = new ArrayList<>(channelPools.size());

        for (var pool: channelPools) {
            futures.add(pool.call(channel -> IndexApiGrpc.newFutureStub(channel)
                            .withExecutor(executor)
                            .withDeadlineAfter(Duration.ofMillis((int) (1.5 * indexRequest.getQueryLimits().getTimeoutMs()))),
                    IndexApiGrpc.IndexApiFutureStub::query,
                    indexRequest));
        }

        for (ListenableFuture<RpcIndexQueryResponse> future: futures) {
            try {
                Instant now = Instant.now();
                if (now.isAfter(bailInstant)) {
                    if (future.isDone()) {
                        results.addAll(future.resultNow().getResultsList());
                    }
                }
                else {
                    results.addAll(future.get(Duration.between(now, bailInstant).toMillis(), TimeUnit.MILLISECONDS).getResultsList());
                }
            }
            catch (ExecutionException ex) {
                if (ex.getCause() instanceof StatusRuntimeException sre) {
                    if (sre.getStatus() == Status.DEADLINE_EXCEEDED) {
                        logger.warn("Timeout: {}", sre.getMessage());
                    }
                    else if (sre.getStatus() == Status.INTERNAL) {
                        logger.warn("Internal Error in index: {}", sre);
                    }
                    else {
                        logger.error("Error while fetching results", ex.getCause());
                    }
                }
                else {
                    logger.error("Error while fetching results", ex.getCause());
                }
            }
            catch (TimeoutException e) {
                future.cancel(true);
                logger.error("Index request timeout");
            }
            catch (Exception e) {
                future.cancel(true);
                logger.error("Error while fetching results", e);
            }
        }

        results.removeIf(item -> isBlacklisted(item, filterTier));
        results.sort(comparator);

        int sublistStart = Math.max(0, (pagination.page - 1) * pagination.pageSize);
        int sublistEnd = Math.min(results.size(), sublistStart + pagination.pageSize);

        List<RpcDecoratedResultItem> ret;

        if (sublistStart < sublistEnd) ret = results.subList(sublistStart, sublistEnd);
        else ret = List.of();

        return new AggregateQueryResponse(ret, pagination.page(), totalNumResults.get());
    }

    static String[] tierNames = {
            "OFF",
            "DANGER",
            "NSFW"
    };

    private boolean isBlacklisted(RpcDecoratedResultItem item, int filterTier) {
        int domainId = UrlIdCodec.getDomainId(item.getRawItem().getCombinedId());

        if (blacklist.isBlacklisted(domainId)) {
            return true;
        }
        if (nsfwDomainFilter.isBlocked(domainId, filterTier)) {
            wmsa_index_query_count.labelValues(tierNames[filterTier]).inc();
            return true;
        }
        return false;
    }

}
