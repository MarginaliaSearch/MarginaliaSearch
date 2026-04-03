package nu.marginalia.api.feeds;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.RpcUrlInfo;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;

import javax.annotation.CheckReturnValue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

@Singleton
public class FeedsClient {
    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");
    private static final ExecutorService executorService = useLoom ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newCachedThreadPool();

    private final GrpcSingleNodeChannelPool<FeedApiGrpc.FeedApiBlockingStub> channelPool;

    private final int realTimePartition;
    private final IndexClient indexClient;

    @Inject
    public FeedsClient(GrpcChannelPoolFactory factory,
                       MqPersistence mqPersistence,
                       IndexClient indexClient,
                       NodeConfigurationService nodeConfigurationService,
                       ServiceConfiguration serviceConfiguration) {
        this.indexClient = indexClient;

        // The client is only interested in the primary node
        var key = ServiceKey.forGrpcApi(FeedApiGrpc.class, ServicePartition.any());

        channelPool = factory.createSingle(key, FeedApiGrpc::newBlockingStub);

        realTimePartition = nodeConfigurationService.getAll().stream()
                .filter(config -> config.profile() == NodeProfile.REALTIME)
                .mapToInt(NodeConfiguration::node)
                .findAny().orElse(-1);
    }

    public CompletableFuture<RpcFeed> getFeed(int domainId) {
        try {
            return channelPool.call(FeedApiGrpc.FeedApiBlockingStub::getFeed)
                    .async(executorService)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build())
                    .thenApply(this::decorateResult);
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // https://www.youtube.com/watch?v=y8OnoxKotPQ
    private RpcFeed decorateResult(RpcFeed rpcFeed) {
        if (realTimePartition < 0)
            return rpcFeed;

        List<RpcFeedItem> oldFeedItems = rpcFeed.getItemsList();
        List<String> urls = new ArrayList<>(rpcFeed.getItemsCount());

        for (var item: oldFeedItems) {
            urls.add(item.getUrl());
        }

        Map<String, RpcUrlInfo> urlDetails = indexClient.getUrlDetails(realTimePartition, urls);

        List<RpcFeedItem> newFeedItems = new ArrayList<>(oldFeedItems.size());

        for (var oldItem: oldFeedItems) {
            var details = urlDetails.get(oldItem.getUrl());
            if (details == null) {
                newFeedItems.add(oldItem);
            }
            else {
                newFeedItems.add(RpcFeedItem.newBuilder(oldItem)
                        .setTitle(details.getTitle())
                        .setDescription(details.getDescription())
                        .build());
            }
        }

        return RpcFeed.newBuilder(rpcFeed)
                .clearItems()
                .addAllItems(newFeedItems)
                .build();
    }


}
