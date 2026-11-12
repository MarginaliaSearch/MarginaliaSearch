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
import org.junit.jupiter.api.io.TempDir;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedFetcherUpdateTest {
    @TempDir
    Path tempDir;

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
    void setUp() throws Exception {
        when(db.createWriter()).thenReturn(writer);
        when(writer.getDbPath()).thenReturn(Files.createTempFile(tempDir, "feeds", ".db"));
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

    @Test
    void batchDeadline() throws Exception {
        when(db.getAllFeeds()).thenReturn(Collections.nCopies(100, definition));
        var active = new AtomicInteger();
        var entered = new AtomicInteger();
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenAnswer(invocation -> {
            active.incrementAndGet();
            entered.incrementAndGet();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Expected cancellation");
            }
            finally {
                active.decrementAndGet();
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH, Duration.ofMillis(250)));

        assertTrue(entered.get() > 0);
        assertTrue(entered.get() <= 64);
        assertEquals(0, active.get());
        verify(db, never()).switchDb(any());
        verify(writer).close();
        verify(writer, never()).saveFeed(any());
    }

    @Test
    void workerFailure() throws Exception {
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class)))
                .thenThrow(new IllegalStateException("Coordinator unavailable"));

        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);

        verify(db, never()).switchDb(any());
        verify(writer).close();
    }

    @Test
    void coordinatorWrites() throws Exception {
        when(db.getAllFeeds()).thenReturn(Collections.nCopies(100, definition));
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenReturn(Optional.empty());
        Thread caller = Thread.currentThread();
        doAnswer(invocation -> {
            assertSame(caller, Thread.currentThread());
            return null;
        }).when(writer).saveFeed(any());

        fetcher.updateFeedsSynchronous(FeedFetcherService.UpdateMode.REFRESH);

        verify(writer, times(100)).saveFeed(oldData);
        verify(db).switchDb(writer);
    }

    @Test
    void stopWorkers() throws Exception {
        var entered = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Expected cancellation");
            }
            finally {
                released.countDown();
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try {
                fetcher.start(FeedFetcherService.UpdateMode.REFRESH);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                fetcher.start(FeedFetcherService.UpdateMode.REFRESH);
            }
            finally {
                fetcher.stop();
            }
        });

        assertFalse(fetcher.isRunning());
        assertEquals(0, released.getCount());
        assertFalse(Files.exists(writer.getDbPath()));
        verify(db).createWriter();
        verify(db, never()).switchDb(any());
        verify(writer).close();
        verify(writer, never()).saveFeed(any());
    }

    @Test
    void stopBeforePublication() throws Exception {
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenReturn(Optional.empty());
        var saving = new CountDownLatch(1);
        doAnswer(invocation -> {
            saving.countDown();
            try {
                new CountDownLatch(1).await();
            }
            catch (InterruptedException ignored) {
                // Exercise the stop flag even if an intervening call clears the interruption.
            }
            return null;
        }).when(writer).saveFeed(any());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try {
                fetcher.start(FeedFetcherService.UpdateMode.REFRESH);
                assertTrue(saving.await(2, TimeUnit.SECONDS));
            }
            finally {
                fetcher.stop();
            }
        });

        assertFalse(fetcher.isRunning());
        assertFalse(Files.exists(writer.getDbPath()));
        verify(db, never()).switchDb(any());
        verify(writer).close();
    }

    @Test
    void restartAfterStop() throws Exception {
        when(coordinator.tryLockDomain(eq(domain), any(Duration.class))).thenReturn(Optional.empty());
        fetcher.stop();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try {
                fetcher.start(FeedFetcherService.UpdateMode.REFRESH);
                while (fetcher.isRunning()) {
                    Thread.sleep(1);
                }
            }
            finally {
                fetcher.stop();
            }
        });

        verify(db).switchDb(writer);
    }
}
