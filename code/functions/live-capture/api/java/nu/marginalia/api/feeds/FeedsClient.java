package nu.marginalia.api.feeds;

import com.google.inject.Inject;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedsClient {
    private static final Logger logger = LoggerFactory.getLogger(FeedsClient.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final GrpcSingleNodeChannelPool<FeedApiGrpc.FeedApiBlockingStub> channelPool;

    @Inject
    public FeedsClient(GrpcChannelPoolFactory factory) {
        // The client is only interested in the primary node
        var key = ServiceKey.forGrpcApi(FeedApiGrpc.class, ServicePartition.any());
        this.channelPool = factory.createSingle(key, FeedApiGrpc::newBlockingStub);
    }


    public CompletableFuture<RpcFeed> getFeed(int domainId) {
        try {
            return channelPool.call(FeedApiGrpc.FeedApiBlockingStub::getFeed)
                    .async(executorService)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build());
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public void updateFeeds() {
        try {
            channelPool.call(FeedApiGrpc.FeedApiBlockingStub::updateFeeds)
                    .run(Empty.getDefaultInstance());
        }
        catch (Exception e) {
            logger.error("API Exception", e);
        }
    }

}
