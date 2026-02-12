package nu.marginalia.ping;

import com.google.inject.Inject;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.ping.fetcher.PingDnsFetcher;
import nu.marginalia.ping.model.*;
import nu.marginalia.ping.svc.DnsPingService;
import nu.marginalia.ping.svc.HttpPingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** PingJobScheduler is responsible for scheduling and processing ping jobs
 * for both HTTP pings and DNS lookups. It manages a queue of jobs and processes them
 * in separate threads, ensuring that domains are pinged and DNS records are updated
 * efficiently.
 */
public class PingJobScheduler {
    private final HttpPingService httpPingService;
    private final DnsPingService dnsPingService;
    private final DomainCoordinator domainCoordinator;
    private final PingDnsFetcher dnsFetcher;
    private final PingDao pingDao;

    private static final Logger logger = LoggerFactory.getLogger(PingJobScheduler.class);

    private static final UpdateSchedule<RootDomainReference, RootDomainReference> dnsUpdateSchedule
            = new UpdateSchedule<>(250_000);
    private static final UpdateSchedule<DomainReference, HistoricalAvailabilityData> availabilityUpdateSchedule
            = new UpdateSchedule<>(250_000);

    public volatile Instant dnsLastSync = Instant.now();
    public volatile Instant availabilityLastSync = Instant.now();

    public volatile boolean ready = false;
    public volatile boolean running = false;

    private final List<Thread> allThreads = new ArrayList<>();

    @Inject
    public PingJobScheduler(HttpPingService httpPingService,
                            DnsPingService dnsPingService,
                            DomainCoordinator domainCoordinator,
                            PingDnsFetcher dnsFetcher,
                            PingDao pingDao)
    {
        this.httpPingService = httpPingService;
        this.dnsPingService = dnsPingService;
        this.domainCoordinator = domainCoordinator;
        this.dnsFetcher = dnsFetcher;
        this.pingDao = pingDao;
    }

    public void run(Instant endTs) {
        running = true;

        availabilityUpdateSchedule.replaceQueue(pingDao.getDomainUpdateSchedule(Integer.MAX_VALUE));
        dnsUpdateSchedule.replaceQueue(pingDao.getDnsUpdateSchedule(50_000));

        dnsLastSync = Instant.now();
        availabilityLastSync = Instant.now();

        int availabilityThreads = Integer.getInteger("ping.availabilityThreads", 8);
        int pingThreads = Integer.getInteger("ping.dnsThreads", 2);

        for (int i = 0; i < availabilityThreads; i++) {
            allThreads.add(Thread.ofPlatform().daemon().name("availability-job-consumer-" + i).start(this::availabilityJobConsumer));
        }
        for (int i = 0; i < pingThreads; i++) {
            allThreads.add(Thread.ofPlatform().daemon().name("dns-job-consumer-" + i).start(this::dnsJobConsumer));
        }


        while (Instant.now().isBefore(endTs)) {

            if (!availabilityUpdateSchedule.hasAvailableJobs()) {
                logger.info("Refilling domain jobs");
                availabilityUpdateSchedule.replaceQueue(pingDao.getDomainUpdateSchedule(Integer.MAX_VALUE));
            }
            if (!dnsUpdateSchedule.hasAvailableJobs()) {
                logger.info("Refilling DNS jobs");
                dnsUpdateSchedule.replaceQueue(pingDao.getDnsUpdateSchedule(50_000));
            }

            try {
                Thread.sleep(Duration.ofSeconds(1));
            }
            catch (InterruptedException ex) {
                break;
            }
        }

        stop();
    }

    public void stop() {
        running = false;

        for (Thread thread : allThreads) {
            try {
                thread.join(Duration.ofSeconds(30));
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Failed to join thread: " + thread.getName(), e);
            }
        }

        dnsFetcher.shutDown();

        synchronized (this) {
            notifyAll();
        }
    }

    private ConcurrentHashMap<String, Instant> lastDomainReschedule = new ConcurrentHashMap<>();

    private void availabilityJobConsumer() {
        while (running && !Thread.interrupted()) {
            try {
                DomainReference ref = availabilityUpdateSchedule.nextIf(domain -> {
                    EdgeDomain domainObj = new EdgeDomain(domain.domainName());
                    if (!domainCoordinator.isLockableHint(domainObj)) {
                        return false; // Skip locked domains
                    }
                    return true; // Process this domain
                });

                if (ref == null)
                    continue;

                long nextId = ref.domainId();
                var data = pingDao.getHistoricalAvailabilityData(nextId);
                if (data == null) {
                    logger.warn("No availability data found for ID: {}", nextId);
                    continue; // No data to process, skip this iteration
                }

                var edgeDomain = ref.asEdgeDomain();

                var maybeLock = domainCoordinator
                        .tryLockDomain(ref.asEdgeDomain(), Duration.ofSeconds(1));

                // If we can't acquire a domain lock in a timely manner, we'll reschedule this job in the future

                if (maybeLock.isEmpty()) {
                    Instant newScheduledTime = lastDomainReschedule.compute(edgeDomain.topDomain,
                            (k,v) -> {
                                if (v == null) {
                                    return Instant.now().plus(Duration.ofSeconds(10));
                                } else {
                                    return v.plus(Duration.ofSeconds(5));
                                }
                            });
                    availabilityUpdateSchedule.add(ref, newScheduledTime);
                    continue;
                }

                var lock = maybeLock.get();
                List<WritableModel> objects;
                try (lock) {
                    objects = switch (data) {
                        case HistoricalAvailabilityData.JustDomainReference(DomainReference reference) ->
                                httpPingService.pingDomain(reference, null, null);
                        case HistoricalAvailabilityData.JustAvailability(
                                String domain, DomainAvailabilityRecord record
                        ) -> httpPingService.pingDomain(
                                new DomainReference(record.domainId(), record.nodeId(), domain), record, null);
                        case HistoricalAvailabilityData.AvailabilityAndSecurity(
                                String domain, DomainAvailabilityRecord availability, DomainSecurityRecord security
                        ) -> httpPingService.pingDomain(
                                new DomainReference(availability.domainId(), availability.nodeId(), domain), availability, security);
                    };
                }

                pingDao.write(objects);

                // Re-schedule the next update time for the domain
                for (var object : objects) {
                    var ts = object.nextUpdateTime();
                    if (ts != null) {
                        availabilityUpdateSchedule.add(ref, ts);
                        break;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Availability job consumer interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("Error processing availability job", e);
            }
        }
    }

    private void dnsJobConsumer() {
        while (running && !Thread.interrupted()) {
            try {
                RootDomainReference ref = dnsUpdateSchedule.next();
                if (ref == null) {
                    continue;
                }

                try {
                    List<WritableModel> objects = switch(ref) {
                        case RootDomainReference.ByIdAndName(long id, String name) -> {
                            var oldRecord = Objects.requireNonNull(pingDao.getDomainDnsRecord(id));
                            yield dnsPingService.pingDomain(oldRecord.rootDomainName(), oldRecord);
                        }
                        case RootDomainReference.ByName(String name) -> {
                            @Nullable var oldRecord = pingDao.getDomainDnsRecord(name);
                            yield dnsPingService.pingDomain(name, oldRecord);
                        }
                    };

                    pingDao.write(objects);

                    // Re-schedule the next update time for the domain
                    for (var object : objects) {
                        var ts = object.nextUpdateTime();
                        if (ts != null) {
                            dnsUpdateSchedule.add(ref, ts);
                            break;
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Error processing DNS job for domain: " + ref, e);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("DNS job consumer interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("Error processing DNS job", e);
            }
        }
    }

}
