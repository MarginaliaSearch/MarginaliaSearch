package nu.marginalia.index.api;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.index.UnrankedCursor;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.nsfw.document.NsfwDocumentFilter;
import nu.marginalia.nsfw.domain.NsfwDomainFilter;
import nu.marginalia.service.NodeConfigurationWatcherIf;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final List<GrpcSingleNodeChannelPool<IndexApiGrpc.IndexApiFutureStub>> channelPools;
    private final Int2ObjectMap<GrpcSingleNodeChannelPool<IndexApiGrpc.IndexApiFutureStub>> poolById = new Int2ObjectOpenHashMap<>();
    private final DomainBlacklistImpl blacklist;
    private final NsfwDomainFilter nsfwDomainFilter;
    private final NsfwDocumentFilter nsfwDocumentFilter;



    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");
    private static final ExecutorService executor = useLoom ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newCachedThreadPool();

    // Bound the number of index queries executed concurrently
    private static final int maxConcurrentQueries = Integer.getInteger("index.query.maxConcurrentQueries", 256);
    private final Semaphore queryThrottle = new Semaphore(maxConcurrentQueries);

    private static final Counter wmsa_index_query_count = Counter.builder()
            .name("wmsa_nsfw_filter_result_count")
            .labelNames("tier")
            .help("Count of results filtered by NSFW tier")
            .register();
    private static final Gauge wmsa_index_query_inflight = Gauge.builder()
            .name("wmsa_index_query_inflight")
            .help("Index queries currently executing")
            .register();
    private static final Counter wmsa_index_query_rejected = Counter.builder()
            .name("wmsa_index_query_rejected")
            .help("Index queries rejected")
            .register();
    private static final Counter wmsa_index_query_cancelled = Counter.builder()
            .name("wmsa_index_query_cancelled")
            .help("Index queries abandoned by the caller before completion")
            .register();
    private static final Counter wmsa_index_query_node_overloaded = Counter.builder()
            .name("wmsa_index_query_node_overloaded")
            .help("Index queries rejected by an index partition reporting overload")
            .register();

    @Inject
    public IndexClient(GrpcChannelPoolFactoryIf channelPoolFactory,
                       DomainBlacklistImpl blacklist,
                       NsfwDomainFilter nsfwDomainFilter,
                       NsfwDocumentFilter nsfwDocumentFilter,
                       NodeConfigurationWatcherIf nodeConfigurationWatcher
                       ) {
        this.nsfwDocumentFilter = nsfwDocumentFilter;
        channelPools = new ArrayList<>();

        for (int node: nodeConfigurationWatcher.getQueryNodes()) {
            var pool = channelPoolFactory.createSingle(ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.partition(node)), IndexApiGrpc::newFutureStub);
            channelPools.add(pool);
            poolById.put(node, pool);
        }

        this.blacklist = blacklist;
        this.nsfwDomainFilter = nsfwDomainFilter;
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);

    public record AggreagateUnrankedQueryResponse(List<RpcDecoratedResultItem> results,
                                                  UnrankedCursor cursor) {

    }

    public AggreagateUnrankedQueryResponse executeQueries(RpcIndexUnrankedQuery unrankedQueryPrototype,
                                                          UnrankedCursor cursor)
    {
        boolean acquired = false;
        try {
            if (Context.current().isCancelled()) {
                wmsa_index_query_cancelled.inc();
                throw Status.CANCELLED.withDescription("Request abandoned by caller").asRuntimeException();
            }

            long queueBudgetMs = clampToRequestDeadline(unrankedQueryPrototype.getQueryLimits().getTimeoutMs() / 2, 0);

            acquired = queryThrottle.tryAcquire(Math.max(0, queueBudgetMs), TimeUnit.MILLISECONDS);
            if (!acquired) {
                wmsa_index_query_rejected.inc();
                throw Status.RESOURCE_EXHAUSTED
                        .withDescription("Too many concurrent index queries in flight")
                        .asRuntimeException();
            }
            wmsa_index_query_inflight.inc();

            return executeQueriesInternal(unrankedQueryPrototype, cursor);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withDescription("Interrupted awaiting query slot").asRuntimeException();
        }
        finally {
            if (acquired) {
                wmsa_index_query_inflight.dec();
                queryThrottle.release();
            }
        }
    }


    private AggreagateUnrankedQueryResponse executeQueriesInternal(RpcIndexUnrankedQuery unrankedQueryPrototype,
                                                                   UnrankedCursor packedCursor) {

        long timeoutMs = unrankedQueryPrototype.getQueryLimits().getTimeoutMs();
        long fanOutBudgetMs = clampToRequestDeadline(2 * timeoutMs, 50);

        if (fanOutBudgetMs <= 0 || Context.current().isCancelled()) {
            wmsa_index_query_cancelled.inc();
            throw Status.CANCELLED.withDescription("Request abandoned by caller").asRuntimeException();
        }

        Instant bailInstant = Instant.now().plusMillis(fanOutBudgetMs);

        Map<Integer, List<RpcDecoratedResultItem>> results = new LinkedHashMap<>(channelPools.size());
        Map<Integer, Map.Entry<GrpcSingleNodeChannelPool.ConnectionHolder, ListenableFuture<RpcIndexQueryResponse>>> futures
                = new LinkedHashMap<>(channelPools.size());

        // Decode the cursor into a map of node -> position
        Int2LongArrayMap cursor = new Int2LongArrayMap();
        switch (packedCursor) {
            case UnrankedCursor.Terminal() -> {
                throw new IllegalArgumentException("Terminal cursor not supported");
            }
            case UnrankedCursor.Uninitialized() -> {
                for (int node: poolById.keySet()) {
                    cursor.put(node, 0);
                }
            }
            case UnrankedCursor.Partial(IntList nodes, LongList positions) -> {
                for (int i = 0; i < nodes.size(); i++) {
                    int node = nodes.getInt(i);
                    long position = positions.getLong(i);
                    cursor.put(node, position);
                }
            }
        }

        // Execute the queries on each node
        for (var entry : cursor.int2LongEntrySet()) {
            var pool = poolById.get(entry.getIntKey());
            if (pool == null) {
                logger.warn("Node {} found in cursor, but not in channel pool", entry.getIntKey());
                continue;
            }

            int node = entry.getIntKey();
            long afterId = entry.getLongValue();

            var holder = pool.getBestConnectionHolder();
            var channel = holder.map(GrpcSingleNodeChannelPool.ConnectionHolder::get);

            if (channel.isEmpty())
                continue;

            var fut = IndexApiGrpc.newFutureStub(channel.get())
                    .withExecutor(executor)
                    .withDeadlineAfter(Duration.ofMillis(Math.min((long) (1.5 * timeoutMs), fanOutBudgetMs)))
                    .unrankedQuery(RpcIndexUnrankedQuery.newBuilder(unrankedQueryPrototype).setAfterId(afterId).build());

            futures.put(node, Map.entry(holder.get(), fut));
        }

        IntSet failedNodes = new IntOpenHashSet();
        IntSet finishedNodes = new IntOpenHashSet();
        Int2LongArrayMap lastIds = new Int2LongArrayMap();

        // Handle the results of the queries
        for (var entry: futures.entrySet()) {
            int node = entry.getKey();

            var holderAndFuture = entry.getValue();
            var holder = holderAndFuture.getKey();
            var future = holderAndFuture.getValue();

            boolean wasSuccess = handleResults(holder, bailInstant, future, (res) -> {
                results.put(node, res.getResultsList());

                if (res.getFinished()) {
                    finishedNodes.add(node);
                }
                else {
                    lastIds.put(node, res.getLastResultId());
                }
            });

            if (!wasSuccess) {
                failedNodes.add(node);
            }
        }

        // Construct a new cursor
        IntArrayList newCursorNodes = new IntArrayList();
        LongArrayList newCursorPositions = new LongArrayList();

        for (var entry: results.entrySet()) {
            int node = entry.getKey();
            var resultsList = entry.getValue();

            if (!finishedNodes.contains(node)) {
                newCursorNodes.add(node);
                newCursorPositions.add(lastIds.get(node));
            }
        }

        for (var failedNode: failedNodes) {
            newCursorNodes.add(failedNode);
            newCursorPositions.add(cursor.get(failedNode));
        }

        UnrankedCursor newCursor = UnrankedCursor.forPositions(newCursorNodes, newCursorPositions);

        // Grab the results
        List<RpcDecoratedResultItem> ret = new ArrayList<>();
        for (var res: results.values()) {
            ret.addAll(res);
        }

        return new AggreagateUnrankedQueryResponse(ret, newCursor);
    }

    public record Pagination(int page, int pageSize) {
        public Pagination(RpcQsQueryPagination pagination) {
            this(pagination.getPage(), pagination.getPageSize());
        }

    }

    public record AggregateQueryResponse(List<RpcDecoratedResultItem> results,
                                         int page,
                                         int totalResults
                                     ) {}

    public boolean hasAvailableCapacity() {
        return queryThrottle.availablePermits() > 0;
    }

    private static long clampToRequestDeadline(long budgetMs, long reservedMs) {
        Deadline deadline = Context.current().getDeadline();
        if (deadline == null) {
            return budgetMs;
        }
        return Math.min(budgetMs, deadline.timeRemaining(TimeUnit.MILLISECONDS) - reservedMs);
    }

    /** Execute a query on the index partitions and return the combined results. */
    public AggregateQueryResponse executeQueries(RpcIndexQuery indexRequest, Pagination pagination) {
        boolean acquired = false;
        try {
            if (Context.current().isCancelled()) {
                wmsa_index_query_cancelled.inc();
                throw Status.CANCELLED.withDescription("Request abandoned by caller").asRuntimeException();
            }

            long queueBudgetMs = clampToRequestDeadline(indexRequest.getQueryLimits().getTimeoutMs() / 2, 0);

            acquired = queryThrottle.tryAcquire(Math.max(0, queueBudgetMs), TimeUnit.MILLISECONDS);
            if (!acquired) {
                wmsa_index_query_rejected.inc();
                throw Status.RESOURCE_EXHAUSTED
                        .withDescription("Too many concurrent index queries in flight")
                        .asRuntimeException();
            }
            wmsa_index_query_inflight.inc();
            return executeQueriesInternal(indexRequest, pagination);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withDescription("Interrupted awaiting query slot").asRuntimeException();
        }
        finally {
            if (acquired) {
                wmsa_index_query_inflight.dec();
                queryThrottle.release();
            }
        }
    }

    private AggregateQueryResponse executeQueriesInternal(RpcIndexQuery indexRequest, Pagination pagination) {

        int filterTier = indexRequest.getNsfwFilterTierValue();
        long timeoutMs = indexRequest.getQueryLimits().getTimeoutMs();

        long fanOutBudgetMs = clampToRequestDeadline(2 * timeoutMs, 50);

        if (fanOutBudgetMs <= 0 || Context.current().isCancelled()) {
            wmsa_index_query_cancelled.inc();
            throw Status.CANCELLED.withDescription("Request abandoned by caller").asRuntimeException();
        }

        Instant bailInstant = Instant.now().plusMillis(fanOutBudgetMs);

        List<RpcDecoratedResultItem> results = new ArrayList<>();
        List<Map.Entry<GrpcSingleNodeChannelPool.ConnectionHolder, ListenableFuture<RpcIndexQueryResponse>>> futures
                = new ArrayList<>(channelPools.size());

        for (var pool: channelPools) {
            var holder = pool.getBestConnectionHolder();
            var channel = holder.map(GrpcSingleNodeChannelPool.ConnectionHolder::get);

            if (channel.isEmpty())
                continue;

            var fut = IndexApiGrpc.newFutureStub(channel.get())
                            .withExecutor(executor)
                            .withDeadlineAfter(Duration.ofMillis(Math.min((long) (1.5 * timeoutMs), fanOutBudgetMs)))
                        .query(indexRequest);

            futures.add(Map.entry(holder.get(), fut));
        }

        for (var holderAndFuture: futures) {
            var holder = holderAndFuture.getKey();
            var future = holderAndFuture.getValue();
            handleResults(holder, bailInstant, future,
                    (res) -> results.addAll(res.getResultsList()));
        }

        results.removeIf(item -> isExcluded(item, filterTier));
        results.sort(comparator);

        int totalNumResults = results.size();

        int sublistStart = Math.max(0, (pagination.page - 1) * pagination.pageSize);
        int sublistEnd = Math.min(results.size(), sublistStart + pagination.pageSize);

        List<RpcDecoratedResultItem> ret;

        if (sublistStart < sublistEnd) ret = results.subList(sublistStart, sublistEnd);
        else ret = List.of();

        return new AggregateQueryResponse(ret, pagination.page(), totalNumResults);
    }


    /** Handle the result of a query on an index partition.
     *
     * @return true if the result was handled successfully, false if the query was not handled due to timeout or error.
     * */
    private <T> boolean handleResults(GrpcSingleNodeChannelPool.ConnectionHolder holder,
                                   Instant bailInstant,
                                   ListenableFuture<T> future,
                                   Consumer<T> onSuccess)
    {
        try {
            Instant now = Instant.now();
            if (now.isAfter(bailInstant)) {
                if (future.state() == Future.State.SUCCESS) {
                    onSuccess.accept(future.resultNow());
                    return true;
                }
                else {
                    future.cancel(true);
                }
            }
            else {
                onSuccess.accept(future.get(Duration.between(now, bailInstant).toMillis(), TimeUnit.MILLISECONDS));
                return true;
            }
        }
        catch (ExecutionException ex) {
            if (ex.getCause() instanceof StatusRuntimeException sre) {
                switch (sre.getStatus().getCode()) {
                    case DEADLINE_EXCEEDED -> logger.warn("Timeout: {}", sre.getMessage());
                    case UNAVAILABLE -> {
                        logger.warn("Unavailable: {}", sre.getMessage());
                        holder.flagError();
                    }
                    case INTERNAL -> logger.warn("Internal Error in index: {}", sre);
                    case RESOURCE_EXHAUSTED -> {
                        logger.warn("Index partition overloaded: {}", sre.getMessage());
                        wmsa_index_query_node_overloaded.inc();
                        holder.flagError();
                    }
                    case CANCELLED -> wmsa_index_query_cancelled.inc();
                    default -> logger.error("Error while fetching results", ex.getCause());
                }
            }
            else {
                holder.flagError();
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

        return false;
    }



    static String[] tierNames = {
            "OFF",
            "DANGER",
            "NSFW"
    };

    private boolean isExcluded(RpcDecoratedResultItem item, int filterTier) {
        int domainId = UrlIdCodec.getDomainId(item.getRawItem().getCombinedId());

        if (blacklist.isBlacklisted(domainId)) {
            return true;
        }

        if (nsfwDomainFilter.isBlocked(domainId, filterTier)) {
            wmsa_index_query_count.labelValues(tierNames[filterTier]).inc();
            return true;
        }

        if (filterTier == 2 && nsfwDocumentFilter.isNsfw(item.getTitle(), item.getDescription())) {
            return true;
        }

        return false;
    }

}
