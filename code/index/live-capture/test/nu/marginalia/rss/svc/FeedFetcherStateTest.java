package nu.marginalia.rss.svc;

import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceAdHocTaskHeartbeat;
import nu.marginalia.service.control.ServiceHeartbeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedFetcherStateTest {
    @TempDir
    Path tempDir;

    @Test
    void successiveUpdates() throws Exception {
        var db = new FeedDb(tempDir.resolve("feeds.db"));
        var domain = new EdgeDomain("test.substack.com");
        var oldData = new FeedItems(domain.toString(), "https://test.substack.com/feed", "2026-01-01T00:00:00",
                List.of(new FeedItem("old title", "2026-01-01T00:00:00.000+0000", "description", "https://test.substack.com/old")));
        int today = (int) LocalDate.now().toEpochDay();

        try (var writer = db.createWriter()) {
            writer.saveFeed(oldData);
            writer.saveEtag(domain.toString(), "old-etag");
            writer.saveFetchDate(domain.toString(), today - 8);
            writer.setErrorCount(domain.toString(), 3);
            db.switchDb(writer);
        }

        var coordinator = mock(DomainCoordinator.class);
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenReturn(Optional.of(() -> {}));
        var heartbeat = mock(ServiceHeartbeat.class);
        when(heartbeat.createServiceAdHocTaskHeartbeat(anyString())).thenReturn(mock(ServiceAdHocTaskHeartbeat.class));
        var response = new AtomicReference<FeedFetcherService.FetchResult>(
                new FeedFetcherService.FetchResult.Success("<html>Unavailable</html>", "bad-etag"));
        var fetcher = new FeedFetcherService(db, mock(DomainTypes.class), coordinator, null, null, heartbeat, null) {
            @Override
            FetchResult fetchFeedData(FeedDefinition feed, ExecutorService executor, String modifiedSince, String etag) {
                return response.get();
            }
        };

        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);
        assertEquals(oldData, db.getFeed(domain));
        assertEquals("old-etag", db.getEtag(domain));
        assertEquals(today - 8, db.getLastFetch(domain));
        assertEquals(4, db.getAllErrorCounts().get(domain.toString()));

        response.set(new FeedFetcherService.FetchResult.Success("<rss><channel/></rss>", "new-etag"));
        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);
        assertEquals(domain.toString(), db.getFeed(domain).domain());
        assertTrue(db.getFeed(domain).items().isEmpty());
        assertEquals("new-etag", db.getEtag(domain));
        assertEquals(today, db.getLastFetch(domain));
        assertTrue(db.getAllErrorCounts().isEmpty());

        clearInvocations(coordinator);
        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);
        verifyNoInteractions(coordinator);
        assertEquals(today, db.getLastFetch(domain));
        assertEquals("new-etag", db.getEtag(domain));
    }
}
