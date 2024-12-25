package nu.marginalia.rss.svc;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.feeds.*;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mq.task.MqLongRunningTask;
import nu.marginalia.mq.task.MqTaskResult;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class FeedsGrpcService extends FeedApiGrpc.FeedApiImplBase implements DiscoverableService  {
    private final FeedDb feedDb;
    private final DbDomainQueries domainQueries;
    private final MqPersistence mqPersistence;
    private final FeedFetcherService feedFetcherService;

    private static final Logger logger = LoggerFactory.getLogger(FeedsGrpcService.class);

    @Inject
    public FeedsGrpcService(FeedDb feedDb,
                            DbDomainQueries domainQueries,
                            MqPersistence mqPersistence,
                            FeedFetcherService feedFetcherService) {
        this.feedDb = feedDb;
        this.domainQueries = domainQueries;
        this.mqPersistence = mqPersistence;
        this.feedFetcherService = feedFetcherService;
    }

    // Ensure that the service is only registered if it is enabled
    @Override
    public boolean shouldRegisterService() {
        return feedDb.isEnabled();
    }

    @Override
    public void updateFeeds(RpcUpdateRequest request,
                            StreamObserver<Empty> responseObserver)
    {
        FeedFetcherService.UpdateMode updateMode = switch(request.getMode()) {
            case CLEAN -> FeedFetcherService.UpdateMode.CLEAN;
            case REFRESH -> FeedFetcherService.UpdateMode.REFRESH;
            default -> throw new IllegalStateException("Unexpected value: " + request.getMode());
        };

        // Start a long-running task to update the feeds
        MqLongRunningTask
                .of(request.getMsgId(), "updateFeeds", mqPersistence)
                .asThread(() -> {
                            feedFetcherService.updateFeeds(updateMode);
                            return new MqTaskResult.Success();
                        })
                .start();

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getFeedDataHash(Empty request, StreamObserver<RpcFeedDataHash> responseObserver) {
        if (!feedDb.isEnabled()) {
            responseObserver.onError(new IllegalStateException("Feed database is disabled on this node"));
            return;
        }

        try {
            String hash = feedDb.getDataHash();
            responseObserver.onNext(RpcFeedDataHash.newBuilder().setHash(hash).build());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            logger.error("Error getting feed data hash", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getUpdatedLinks(RpcUpdatedLinksRequest request, StreamObserver<RpcUpdatedLinksResponse> responseObserver) {
        Instant since = Instant.ofEpochMilli(request.getSinceEpochMillis());

        try {
            feedDb.getLinksUpdatedSince(since, (String domain, List<String> urls) -> {
                RpcUpdatedLinksResponse rsp = RpcUpdatedLinksResponse.newBuilder()
                        .setDomain(domain)
                        .addAllUrl(urls)
                        .build();
                responseObserver.onNext(rsp);
            });

            responseObserver.onCompleted();
        }
        catch (Exception e) {
            logger.error("Error getting updated links", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getFeed(RpcDomainId request,
                        StreamObserver<RpcFeed> responseObserver) {
        if (!feedDb.isEnabled()) {
            responseObserver.onError(new IllegalStateException("Feed database is disabled on this node"));
            return;
        }

        Optional<EdgeDomain> domainName = domainQueries.getDomain(request.getDomainId());
        if (domainName.isEmpty()) {
            responseObserver.onError(new IllegalArgumentException("Domain not found"));
            return;
        }

        FeedItems feedItems = feedDb.getFeed(domainName.get());

        RpcFeed.Builder retB = RpcFeed.newBuilder()
                .setDomainId(request.getDomainId())
                .setDomain(domainName.get().toString())
                .setFeedUrl(feedItems.feedUrl())
                .setUpdated(feedItems.updated())
                .setFetchTimestamp(feedDb.getFetchTime().toEpochMilli());

        for (var item : feedItems.items()) {
            retB.addItemsBuilder()
                    .setTitle(item.title())
                    .setUrl(item.url())
                    .setDescription(item.description())
                    .setDate(item.date())
                    .build();
        }

        responseObserver.onNext(retB.build());
        responseObserver.onCompleted();
    }
}
