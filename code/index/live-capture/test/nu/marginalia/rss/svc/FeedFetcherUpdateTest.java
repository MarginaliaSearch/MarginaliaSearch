package nu.marginalia.rss.svc;

import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.db.FeedDbWriter;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceAdHocTaskHeartbeat;
import nu.marginalia.service.control.ServiceHeartbeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class FeedFetcherUpdateTest {
    private final FeedDb db = mock(FeedDb.class);
    private final FeedDbWriter writer = mock(FeedDbWriter.class);
    private final DomainCoordinator coordinator = mock(DomainCoordinator.class);
    private final ServiceHeartbeat heartbeat = mock(ServiceHeartbeat.class);
    private final FeedFetcherService fetcher = new FeedFetcherService(db, mock(DomainTypes.class), coordinator,
            null, null, heartbeat, null);
    private final EdgeDomain domain = new EdgeDomain("example.com");
    private final FeedDefinition definition = new FeedDefinition(domain.toString(), "https://example.com/feed", "");
    private final FeedItems oldData = new FeedItems(domain.toString(), definition.feedUrl(), "", List.of());

    @BeforeEach
    void setUp() {
        when(db.createWriter()).thenReturn(writer);
        when(db.getAllFeeds()).thenReturn(List.of(definition));
        when(db.getAllErrorCounts()).thenReturn(Map.of(domain.toString(), 3));
        when(db.getFeed(domain)).thenReturn(oldData);
        when(db.getEtag(domain)).thenReturn("etag");
        when(db.getLastFetch(domain)).thenReturn(42);
        when(heartbeat.createServiceAdHocTaskHeartbeat(anyString())).thenReturn(mock(ServiceAdHocTaskHeartbeat.class));
    }

    @Test
    void busyDomain() throws Exception {
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenReturn(Optional.empty());

        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);

        verify(coordinator).tryLockDomain(eq(domain), any(Duration.class));
        verify(coordinator, never()).lockDomain(any());
        verify(writer).saveFeed(oldData);
        verify(writer).saveEtag(domain.toString(), "etag");
        verify(writer).saveFetchDate(domain.toString(), 42);
        verify(writer).setErrorCount(domain.toString(), 3);
        verify(db).switchDb(writer);
    }
}
