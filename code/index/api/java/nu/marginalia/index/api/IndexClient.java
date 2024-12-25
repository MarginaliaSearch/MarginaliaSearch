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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.clamp;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;
    private final DomainBlacklistImpl blacklist;
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

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
        List<CompletableFuture<Iterator<RpcDecoratedResultItem>>> futures =
                channelPool.call(IndexApiGrpc.IndexApiBlockingStub::query)
                        .async(executor)
                        .runEach(indexRequest);

        final int requestedMaxResults = indexRequest.getQueryLimits().getResultsTotal();
        final int resultsUpperBound = requestedMaxResults * channelPool.getNumNodes();

        List<RpcDecoratedResultItem> results = new ArrayList<>(resultsUpperBound);

        for (var future : futures) {
            try {
                future.get().forEachRemaining(results::add);
            }
            catch (Exception e) {
                logger.error("Downstream exception", e);
            }
        }

        // Sort the results by ranking score and remove blacklisted domains
        results.sort(comparator);
        results.removeIf(this::isBlacklisted);

        int numReceivedResults = results.size();

        // pagination is typically 1-indexed, so we need to adjust the start and end indices
        int indexStart = (pagination.page - 1) * pagination.pageSize;
        int indexEnd = (pagination.page) * pagination.pageSize;

        results = results.subList(
                clamp(indexStart, 0, Math.max(0, results.size() - 1)), // from is inclusive, so subtract 1 from size()
                clamp(indexEnd, 0, results.size()));

        return new AggregateQueryResponse(results, pagination.page(), numReceivedResults);
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }

}
