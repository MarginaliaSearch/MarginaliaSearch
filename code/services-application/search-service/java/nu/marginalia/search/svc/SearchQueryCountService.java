package nu.marginalia.search.svc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps per-minute statistics of queries */
@Singleton
public class SearchQueryCountService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AtomicInteger lastMinuteQueries = new AtomicInteger();

    private final TimeUnit minute = TimeUnit.of(ChronoUnit.MINUTES);
    private volatile int queriesPerMinute;

    public SearchQueryCountService() {
        Thread updateThread = new Thread(this::updateQueriesPerMinute,
                "SearchVisitorCountService::updateQueriesPerMinute");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    /** Retreive the number of queries performed the minute before this one */
    public int getQueriesPerMinute() {
        return queriesPerMinute;
    }

    /** Update query statistics for presentation */
    public void registerQuery() {
        lastMinuteQueries.incrementAndGet();
    }

    private void updateQueriesPerMinute() {
        try {
            for (;;) {
                queriesPerMinute = lastMinuteQueries.getAndSet(0);
                minute.sleep(1);
            }
        } catch (InterruptedException e) {
            logger.warn("Query counter thread was interrupted");
        }

    }
}
