package nu.marginalia.ping;

import com.google.inject.Inject;
import nu.marginalia.ping.model.*;
import nu.marginalia.ping.svc.DnsPingService;
import nu.marginalia.ping.svc.HttpPingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** PingJobScheduler is responsible for scheduling and processing ping jobs
 * for both HTTP pings and DNS lookups. It manages a queue of jobs and processes them
 * in separate threads, ensuring that domains are pinged and DNS records are updated
 * efficiently.
 */
public class PingJobScheduler {
    private final HttpPingService httpPingService;
    private final DnsPingService dnsPingService;
    private final PingDao pingDao;

    private static final Logger logger = LoggerFactory.getLogger(PingJobScheduler.class);

    private static final UpdateSchedule<RootDomainReference, RootDomainReference> dnsUpdateSchedule
            = new UpdateSchedule<>(250_000);
    private static final UpdateSchedule<Long, HistoricalAvailabilityData> availabilityUpdateSchedule
            = new UpdateSchedule<>(250_000);

    public volatile Instant dnsLastSync = Instant.now();
    public volatile Instant availabilityLastSync = Instant.now();

    public volatile Integer nodeId = null;
    public volatile boolean running = false;

    private final List<Thread> allThreads = new ArrayList<>();

    @Inject
    public PingJobScheduler(HttpPingService httpPingService,
                            DnsPingService dnsPingService,
                            PingDao pingDao)
    {
        this.httpPingService = httpPingService;
        this.dnsPingService = dnsPingService;
        this.pingDao = pingDao;
    }

    public synchronized void start() {
        if (running)
            return;

        nodeId = null;

        running = true;

        allThreads.add(Thread.ofPlatform().daemon().name("sync-dns").start(this::syncAvailabilityJobs));
        allThreads.add(Thread.ofPlatform().daemon().name("sync-availability").start(this::syncDnsRecords));

        int availabilityThreads = Integer.getInteger("ping.availabilityThreads", 8);
        int pingThreads = Integer.getInteger("ping.dnsThreads", 2);

        for (int i = 0; i < availabilityThreads; i++) {
            allThreads.add(Thread.ofPlatform().daemon().name("availability-job-consumer-" + i).start(this::availabilityJobConsumer));
        }
        for (int i = 0; i < pingThreads; i++) {
            allThreads.add(Thread.ofPlatform().daemon().name("dns-job-consumer-" + i).start(this::dnsJobConsumer));
        }
    }

    public void stop() {
        running = false;
        for (Thread thread : allThreads) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Failed to join thread: " + thread.getName(), e);
            }
        }
    }

    public void pause(int nodeId) {
        logger.info("Pausing PingJobScheduler for nodeId: {}", nodeId);

        if (this.nodeId != null && this.nodeId != nodeId) {
            logger.warn("Attempted to pause PingJobScheduler with mismatched nodeId: expected {}, got {}", this.nodeId, nodeId);
            return;
        }
        this.nodeId = null;

        availabilityUpdateSchedule.clear();
        dnsUpdateSchedule.clear();

        logger.info("PingJobScheduler paused");
    }

    public synchronized void enableForNode(int nodeId) {
        logger.info("Resuming PingJobScheduler for nodeId: {}", nodeId);
        if (this.nodeId != null) {
            logger.warn("Attempted to resume PingJobScheduler with mismatched nodeId: expected {}, got {}", this.nodeId, nodeId);
            return;
        }

        availabilityUpdateSchedule.replaceQueue(pingDao.getDomainUpdateSchedule(nodeId));
        dnsUpdateSchedule.replaceQueue(pingDao.getDnsUpdateSchedule(nodeId));
        dnsLastSync = Instant.now();
        availabilityLastSync = Instant.now();

        // Flag that we are running again
        this.nodeId = nodeId;

        notifyAll();
        logger.info("PingJobScheduler resumed");
    }

    public synchronized void waitForResume() throws InterruptedException {
        while (nodeId == null) {
            wait();
        }
    }

    private void availabilityJobConsumer() {
        while (running) {
            try {
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue;
                }

                long nextId = availabilityUpdateSchedule.next();
                var data = pingDao.getHistoricalAvailabilityData(nextId);
                if (data == null) {
                    logger.warn("No availability data found for ID: {}", nextId);
                    continue; // No data to process, skip this iteration
                }

                try {
                    List<WritableModel> objects = switch (data) {
                        case HistoricalAvailabilityData.JustDomainReference(DomainReference reference)
                                -> httpPingService.pingDomain(reference, null, null);
                        case HistoricalAvailabilityData.JustAvailability(String domain, DomainAvailabilityRecord record)
                                -> httpPingService.pingDomain(
                                    new DomainReference(record.domainId(), record.nodeId(), domain), record, null);
                        case HistoricalAvailabilityData.AvailabilityAndSecurity(String domain, DomainAvailabilityRecord availability, DomainSecurityRecord security)
                                -> httpPingService.pingDomain(
                                        new DomainReference(availability.domainId(), availability.nodeId(), domain), availability, security);
                    };

                    pingDao.write(objects);

                    // Re-schedule the next update time for the domain
                    for (var object : objects) {
                        var ts = object.nextUpdateTime();
                        if (ts != null) {
                            availabilityUpdateSchedule.add(nextId, ts);
                            break;
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Error processing availability job for domain: " + data.domain(), e);
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
        while (running) {
            try {
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue;
                }

                RootDomainReference ref = dnsUpdateSchedule.next();

                try {
                    List<WritableModel> objects = switch(ref) {
                        case RootDomainReference.ById(long id) -> {
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

    private void syncAvailabilityJobs() {
        try {
            while (running) {

                // If we are suspended, wait for resume
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue;
                }

                // Check if we need to refresh the availability data
                Instant nextRefresh = availabilityLastSync.plus(Duration.ofHours(24));
                if (Instant.now().isBefore(nextRefresh)) {
                    Duration remaining = Duration.between(Instant.now(), nextRefresh);
                    TimeUnit.MINUTES.sleep(Math.max(1, remaining.toMinutes()));
                    continue;
                }

                availabilityUpdateSchedule.replaceQueue(pingDao.getDomainUpdateSchedule(nid));
                availabilityLastSync = Instant.now();
            }
        }
        catch (Exception e) {
            logger.error("Error fetching new ping jobs", e);
        }
    }

    private void syncDnsRecords() {
        try {
            while (running) {

                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue; // re-fetch the records after resuming
                }

                // Check if we need to refresh the availability data
                Instant nextRefresh = dnsLastSync.plus(Duration.ofHours(24));
                if (Instant.now().isBefore(nextRefresh)) {
                    Duration remaining = Duration.between(Instant.now(), nextRefresh);
                    TimeUnit.MINUTES.sleep(Math.max(1, remaining.toMinutes()));
                    continue;
                }

                dnsUpdateSchedule.replaceQueue(pingDao.getDnsUpdateSchedule(nid));
                dnsLastSync = Instant.now();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("DNS job fetch interrupted", e);
        }
    }


}
