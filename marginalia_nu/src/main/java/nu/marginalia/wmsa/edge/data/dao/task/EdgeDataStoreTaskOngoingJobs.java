package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.Singleton;
import io.prometheus.client.Gauge;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class EdgeDataStoreTaskOngoingJobs {
    private final ConcurrentHashMap<String, Long> indexingDomains = new ConcurrentHashMap<>();
    private static final long STALE_JOB_TIMEOUT = 30*60;

    private static final Gauge wmsa_director_ongoing_jobs = Gauge.build("wmsa_director_ongoing_jobs",
            "wmsa_director_ongoing_jobs").register();

    public EdgeDataStoreTaskOngoingJobs() {
        Schedulers.computation().schedulePeriodicallyDirect(this::purgeOngoingJobs, 60, 60, TimeUnit.SECONDS);
    }


    private void purgeOngoingJobs() {
        final long now = System.currentTimeMillis();

        indexingDomains
                .entrySet()
                .removeIf(e -> (now - e.getValue()) / 1000 >= STALE_JOB_TIMEOUT);

        wmsa_director_ongoing_jobs.set(indexingDomains.size());
    }

    public boolean isOngoing(EdgeDomain domain) {
        return indexingDomains.containsKey(domain.getDomainKey());
    }

    public boolean add(EdgeDomain job) {
        return indexingDomains.putIfAbsent(job.getDomainKey(), System.currentTimeMillis()) == null;
    }

    public void clear() {
        indexingDomains.clear();
    }

    public void remove(EdgeDomain domain) {
        indexingDomains.remove(domain.getDomainKey());
    }
}
