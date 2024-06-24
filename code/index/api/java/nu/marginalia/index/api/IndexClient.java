package nu.marginalia.index.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class IndexClient {
    private static final Logger logger = LoggerFactory.getLogger(IndexClient.class);
    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;
    private static final ExecutorService executor = Executors.newFixedThreadPool(32);

    @Inject
    public IndexClient(GrpcChannelPoolFactory channelPoolFactory) {
        this.channelPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.multi()),
                IndexApiGrpc::newBlockingStub);
    }

    @SneakyThrows
    public List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest) {
        var futures =
                channelPool.call(IndexApiGrpc.IndexApiBlockingStub::query)
                        .async(executor)
                        .runEach(indexRequest);
        List<RpcDecoratedResultItem> results = new ArrayList<>();
        for (var future : futures) {
            try {
                future.get().forEachRemaining(results::add);
            }
            catch (Exception e) {
                logger.error("Downstream exception", e);
            }
        }

        return results;
    }

}
