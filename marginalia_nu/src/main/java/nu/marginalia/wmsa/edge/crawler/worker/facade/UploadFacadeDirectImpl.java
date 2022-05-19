package nu.marginalia.wmsa.edge.crawler.worker.facade;

import com.google.inject.Inject;
import io.prometheus.client.Histogram;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageContent;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class UploadFacadeDirectImpl implements UploadFacade {
    private final EdgeDataStoreDao dataStore;
    private final EdgeIndexClient indexClient;
    private final EdgeDirectorClient directorClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Histogram wmsa_edge_upload_metrics = Histogram
            .build("wmsa_edge_upload_metrics", "upload times")
            .labelNames("action")
            .register();

    @Inject
    public UploadFacadeDirectImpl(EdgeDataStoreDao dataStore,
                                  EdgeIndexClient indexClient,
                                  EdgeDirectorClient directorClient) {
        this.dataStore = dataStore;
        this.indexClient = indexClient;
        this.directorClient = directorClient;
    }

    @Override
    public void putLinks(Collection<EdgeDomainLink> links, boolean wipeExisting) {
        wmsa_edge_upload_metrics
                .labels("putLinks")
                .time(() -> {
                dataStore.putLink(wipeExisting, links.toArray(EdgeDomainLink[]::new));
        });
    }

    @Override
    public void putUrls(Collection<EdgeUrl> urls, double quality) {
        wmsa_edge_upload_metrics
                .labels("putUrls")
                .time(() -> {
                    dataStore.putUrl(quality, urls.toArray(EdgeUrl[]::new));
                });
    }
    @Override
    public void putFeeds(Collection<EdgeUrl> feeds) {
        if (feeds.isEmpty()) {
            return;
        }
        wmsa_edge_upload_metrics
                .labels("putFeeds")
                .time(() -> {
                    dataStore.putFeeds(feeds.toArray(EdgeUrl[]::new));
                });
    }

    @Override
    public void putUrlVisits(Collection<EdgeUrlVisit> visits) {
        wmsa_edge_upload_metrics
                .labels("putUrlVisits")
                .time(() -> {
                    dataStore.putUrlVisited(visits.toArray(EdgeUrlVisit[]::new));
                });
    }

    @Override
    public void putDomainAlias(EdgeDomain src, EdgeDomain dst) {
        wmsa_edge_upload_metrics
                .labels("putDomainAlias")
                .time(() -> {
                    dataStore.putDomainAlias(src, dst);
                });
    }

    @Override
    public void finishTask(EdgeDomain domain, double quality, EdgeDomainIndexingState state) {
        wmsa_edge_upload_metrics
                .labels("finishTask")
                .time(() -> {
                    directorClient.finishTask(Context.internal(), domain, quality, state).blockingSubscribe();
                });
    }

    @Override
    public void putWords(Collection<EdgePageContent> pages, int writer) {
        wmsa_edge_upload_metrics
                .labels("putWords")
                .time(() -> {
                    Flowable.fromIterable(pages)

                            .parallel(4)
                            .flatMap(page -> indexClient
                                    .putWords(Context.internal(),
                                            dataStore.getDomainId(page.url.domain),
                                            dataStore.getUrlId(page.url),
                                            page.metadata.quality(),
                                            page.words,
                                            writer

                                    ).subscribeOn(Schedulers.io())
                                    .retryWhen((Observable<Throwable> f) -> f.take(5).delay(30, TimeUnit.SECONDS))
                                    .toFlowable(BackpressureStrategy.BUFFER)
                                    ).reduce((a,b)->a)
                            .blockingSubscribe();
                });
    }

    @Override
    public boolean isBlacklisted(EdgeDomain domain) {
        return dataStore.isBlacklisted(domain);
    }

    @Override
    public boolean isBlocked() {
        var ctx = Context.internal();

        try {
            return directorClient.isBlocked(ctx).blockingFirst()
                    || indexClient.isBlocked(ctx).blockingFirst();
        }
        catch (Exception ex) {
            return false;
        }
    }

    public void updateDomainIndexTimestamp(EdgeDomain domain, EdgeDomainIndexingState state, EdgeDomain alias, int minIndexed) {
        dataStore.updateDomainIndexTimestamp(domain, state, alias, minIndexed);
    }
}
