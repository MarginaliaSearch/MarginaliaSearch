package nu.marginalia.rss.svc;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.feeds.Empty;
import nu.marginalia.api.feeds.FeedApiGrpc;
import nu.marginalia.api.feeds.RpcDomainId;
import nu.marginalia.api.feeds.RpcFeed;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class FeedsGrpcService extends FeedApiGrpc.FeedApiImplBase implements DiscoverableService  {
    private final FeedDb feedDb;
    private final DbDomainQueries domainQueries;
    private final FeedFetcherService feedFetcherService;

    private static final Logger logger = LoggerFactory.getLogger(FeedsGrpcService.class);

    @Inject
    public FeedsGrpcService(FeedDb feedDb,
                            DbDomainQueries domainQueries,
                            FeedFetcherService feedFetcherService) {
        this.feedDb = feedDb;
        this.domainQueries = domainQueries;
        this.feedFetcherService = feedFetcherService;
    }

    // Ensure that the service is only registered if it is enabled
    @Override
    public boolean shouldRegisterService() {
        return feedDb.isEnabled();
    }

    @Override
    public void updateFeeds(Empty request,
                             StreamObserver<Empty> responseObserver)
    {
        Thread.ofPlatform().start(() -> {
            try {
                feedFetcherService.updateFeeds();
            } catch (IOException e) {
                logger.error("Failed to update feeds", e);
            }
        });

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getFeed(RpcDomainId request,
                        StreamObserver<RpcFeed> responseObserver)
    {
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
                .setUpdated(feedItems.updated());

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
