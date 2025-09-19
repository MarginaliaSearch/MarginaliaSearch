package nu.marginalia.index.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Counter;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;
    private final DomainBlacklistImpl blacklist;
    private final NsfwDomainFilter nsfwDomainFilter;

    Counter wmsa_index_query_count = Counter.build()
            .name("wmsa_nsfw_filter_result_count")
            .labelNames("tier")
            .help("Count of results filtered by NSFW tier")
            .register();


    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");
    private static final ExecutorService executor = useLoom ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newCachedThreadPool();

    @Inject
    public IndexClient(GrpcChannelPoolFactory channelPoolFactory,
                       DomainBlacklistImpl blacklist,
                       NsfwDomainFilter nsfwDomainFilter
                       ) {
        this.channelPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.multi()),
                IndexApiGrpc::newBlockingStub);
        this.blacklist = blacklist;
        this.nsfwDomainFilter = nsfwDomainFilter;
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);

    public record Pagination(int page, int pageSize) {}

    public record AggregateQueryResponse(List<RpcDecoratedResultItem> results,
                                         int page,
                                         int totalResults
                                     ) {}

    /** Execute a query on the index partitions and return the combined results. */
    public AggregateQueryResponse executeQueries(RpcIndexQuery indexRequest, Pagination pagination) {

        final int requestedMaxResults = indexRequest.getQueryLimits().getResultsTotal();
        int filterTier = indexRequest.getNsfwFilterTierValue();
        AtomicInteger totalNumResults = new AtomicInteger(0);

        Instant bailInstant  = Instant.now().plusMillis((int) (1.5 * indexRequest.getQueryLimits().getTimeoutMs()));

        List<RpcDecoratedResultItem> results =
                channelPool.call(IndexApiGrpc.IndexApiBlockingStub::query)
                        .async(executor)
                        .runEach(indexRequest)
                        .stream()
                        .map(future -> future.thenApply(iterator -> {
                            List<RpcDecoratedResultItem> ret = new ArrayList<>(requestedMaxResults);
                            iterator.forEachRemaining(ret::add);
                            totalNumResults.addAndGet(ret.size());
                            return ret;
                        }))
                        .mapMulti((CompletableFuture<List<RpcDecoratedResultItem>> fut, Consumer<List<RpcDecoratedResultItem>> c) ->{
                            try {
                                Instant now = Instant.now();
                                if (now.isAfter(bailInstant)) {
                                    c.accept(fut.get(0, TimeUnit.MILLISECONDS));
                                }
                                else {
                                    c.accept(fut.get(Duration.between(now, bailInstant).toMillis(), TimeUnit.SECONDS));
                                }
                            } catch (Exception e) {
                                logger.error("Error while fetching results", e);
                            }
                        })
                        .flatMap(List::stream)
                        .filter(item -> !isBlacklisted(item, filterTier))
                        .sorted(comparator)
                        .skip(Math.max(0, (pagination.page - 1) * pagination.pageSize))
                        .limit(pagination.pageSize)
                        .toList();

        return new AggregateQueryResponse(results, pagination.page(), totalNumResults.get());
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
            wmsa_index_query_count.labels(tierNames[filterTier]).inc();
            return true;
        }
        return false;
    }

}
