package nu.marginalia.index.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;
    private final DomainBlacklistImpl blacklist;
    private static final ExecutorService executor = Executors.newFixedThreadPool(32);

    @Inject
    public IndexClient(GrpcChannelPoolFactory channelPoolFactory, DomainBlacklistImpl blacklist) {
        this.channelPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.multi()),
                IndexApiGrpc::newBlockingStub);
        this.blacklist = blacklist;
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);


    /** Execute a query on the index partitions and return the combined results. */
    @SneakyThrows
    public List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest) {
        var futures =
                channelPool.call(IndexApiGrpc.IndexApiBlockingStub::query)
                        .async(executor)
                        .runEach(indexRequest);

        final int resultsTotal = indexRequest.getQueryLimits().getResultsTotal();
        final int resultsUpperBound = resultsTotal * channelPool.getNumNodes();

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

        // Keep only as many results as were requested
        if (results.size() > resultsTotal) {
            results = results.subList(0, resultsTotal);
        }

        return results;
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }

}
