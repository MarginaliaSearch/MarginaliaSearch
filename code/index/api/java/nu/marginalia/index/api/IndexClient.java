package nu.marginalia.index.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;
    private final DomainBlacklistImpl blacklist;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Inject
    public IndexClient(GrpcChannelPoolFactory channelPoolFactory, DomainBlacklistImpl blacklist) {
        this.channelPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.multi()),
                IndexApiGrpc::newBlockingStub);
        this.blacklist = blacklist;
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

        AtomicInteger totalNumResults = new AtomicInteger(0);

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
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .filter(item -> !isBlacklisted(item))
                        .sorted(comparator)
                        .skip(Math.max(0, (pagination.page - 1) * pagination.pageSize))
                        .limit(pagination.pageSize)
                        .toList();

        return new AggregateQueryResponse(results, pagination.page(), totalNumResults.get());
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }

}
