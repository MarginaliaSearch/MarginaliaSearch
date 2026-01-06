package nu.marginalia.rss.svc;

import com.google.inject.Inject;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.feeds.*;
import nu.marginalia.config.LiveCaptureConfig;
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
    public void getFeed(RpcDomainId request,
                        StreamObserver<RpcFeed> responseObserver) {
        if (!feedDb.isEnabled()) {
            responseObserver.onError(Status.INTERNAL.withDescription("Feed database is disabled on this node").asRuntimeException());
            return;
        }

        Optional<EdgeDomain> domainName = domainQueries.getDomain(request.getDomainId());
        if (domainName.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Domain not found").asRuntimeException());
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
