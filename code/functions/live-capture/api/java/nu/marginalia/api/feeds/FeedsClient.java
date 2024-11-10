package nu.marginalia.api.feeds;

import com.google.inject.Inject;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedsClient {
    private static final Logger logger = LoggerFactory.getLogger(FeedsClient.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final GrpcSingleNodeChannelPool<FeedApiGrpc.FeedApiBlockingStub> channelPool;
    private final MqPersistence mqPersistence;

    @Inject
    public FeedsClient(GrpcChannelPoolFactory factory, MqPersistence mqPersistence) {
        this.mqPersistence = mqPersistence;
        // The client is only interested in the primary node
        var key = ServiceKey.forGrpcApi(FeedApiGrpc.class, ServicePartition.any());
        this.channelPool = factory.createSingle(key, FeedApiGrpc::newBlockingStub);
    }


    /** Create an appropriately named outbox for the update actor requests */
    public MqOutbox createOutbox(String callerName, int outboxNodeId) {
        return new MqOutbox(mqPersistence, "update-rss-feeds", 1, callerName, outboxNodeId, UUID.randomUUID());
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

    public void updateFeeds(RpcFeedUpdateMode mode, long msgId) {
        try {
            channelPool.call(FeedApiGrpc.FeedApiBlockingStub::updateFeeds)
                    .run(RpcUpdateRequest.newBuilder()
                            .setMode(mode)
                            .setMsgId(msgId)
                            .build()
                    );
        }
        catch (Exception e) {
            logger.error("API Exception", e);
        }
    }

}
