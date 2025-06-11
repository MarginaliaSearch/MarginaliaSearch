package nu.marginalia.ping;

import com.google.inject.Inject;
import nu.marginalia.ping.model.*;
import nu.marginalia.ping.svc.DnsPingService;
import nu.marginalia.ping.svc.HttpPingService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
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
    private final PingDao pingDao;

    private static final Logger logger = LoggerFactory.getLogger(PingJobScheduler.class);

    sealed interface DnsJob {
        Object reference();

        record DnsFetch(String rootDomain) implements DnsJob {
            @Override
            public Object reference() {
                return rootDomain;
            }
        }
        record DnsRefresh(DomainDnsRecord oldRecord) implements DnsJob {
            @Override
            public Object reference() {
                return oldRecord.rootDomainName();
            }
        }
    }

    sealed interface PingJob {
        Object reference();

        record Ping(DomainReference domainReference) implements PingJob {
            @Override
            public Object reference() {
                return domainReference.domainName();
            }
        }
        record PingRefresh(String domain, @NotNull DomainAvailabilityRecord availability, @Nullable DomainSecurityRecord securityRecord) implements PingJob {
            @Override
            public Object reference() {
                return domain;
            }
        }
    }

    // Keeps track of ongoing ping and DNS processing to avoid duplicate work,
    // which is mainly a scenario that will occur when there is not a lot of data
    // in the database.  In real-world scenarios, the queues will be full most
    // of the time, and prevent this from being an issue.

    private static final ConcurrentHashMap<Object, Boolean> processingDomainsPing = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Object, Boolean> processingDomainsDns = new ConcurrentHashMap<>();

    private static final ArrayBlockingQueue<DnsJob> dnsJobQueue = new ArrayBlockingQueue<>(8);
    private static final ArrayBlockingQueue<PingJob> pingJobQueue = new ArrayBlockingQueue<>(8);

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

    public synchronized void start(boolean startPaused) {
        if (running)
            return;

        nodeId = null;

        running = true;

        allThreads.add(Thread.ofPlatform().daemon().name("new-dns").start(this::fetchNewDnsRecords));
        allThreads.add(Thread.ofPlatform().daemon().name("new-pings").start(this::fetchNewPingJobs));
        allThreads.add(Thread.ofPlatform().daemon().name("update-pings").start(this::updatePingJobs));
        allThreads.add(Thread.ofPlatform().daemon().name("update-dns").start(this::updateDnsJobs));

        for (int i = 0; i < 4; i++) {
            allThreads.add(Thread.ofPlatform().daemon().name("ping-job-consumer-" + i).start(this::pingJobConsumer));
        }
        for (int i = 0; i < 4; i++) {
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
        if (this.nodeId != null && this.nodeId != nodeId) {
            logger.warn("Attempted to pause PingJobScheduler with mismatched nodeId: expected {}, got {}", this.nodeId, nodeId);
            return;
        }
        this.nodeId = null;
        logger.info("PingJobScheduler paused");
    }

    public synchronized void resume(int nodeId) {
        if (this.nodeId != null) {
            logger.warn("Attempted to resume PingJobScheduler with mismatched nodeId: expected null, got {}", this.nodeId, nodeId);
            return;
        }
        this.nodeId = nodeId;

        notifyAll();
        logger.info("PingJobScheduler resumed");
    }

    public synchronized void waitForResume() throws InterruptedException {
        while (nodeId == null) {
            wait();
        }
    }

    private void pingJobConsumer() {
        while (running) {
            try {
                PingJob job = pingJobQueue.poll(1, TimeUnit.SECONDS);
                if (job == null) {
                    continue; // No job available, continue to the next iteration
                }

                try {
                    switch (job) {
                        case PingJob.Ping(DomainReference reference) -> {
                            logger.info("Pinging domain: {}", reference.domainName());
                            pingDao.write(httpPingService.pingDomain(reference, null, null));
                        }
                        case PingJob.PingRefresh(String domain, DomainAvailabilityRecord availability, DomainSecurityRecord security) -> {
                            logger.info("Pinging domain with reference: {}", domain);
                            pingDao.write(httpPingService.pingDomain(
                                    new DomainReference(availability.domainId(), availability.nodeId(), domain),
                                    availability,
                                    security));
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Error processing ping job for domain: " + job.reference(), e);
                }
                finally {
                    // Remove the domain from the processing map
                    processingDomainsPing.remove(job.reference());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Ping job consumer interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("Error processing ping job", e);
            }
        }
    }

    private void dnsJobConsumer() {
        while (running) {
            try {
                DnsJob job = dnsJobQueue.poll(1, TimeUnit.SECONDS);
                if (job == null) {
                    continue; // No job available, continue to the next iteration
                }

                try {
                    switch (job) {
                        case DnsJob.DnsFetch(String rootDomain) -> {
                            logger.info("Fetching DNS records for root domain: {}", rootDomain);
                            pingDao.write(dnsPingService.pingDomain(rootDomain, null));
                        }
                        case DnsJob.DnsRefresh(DomainDnsRecord oldRecord) -> {
                            logger.info("Refreshing DNS records for domain: {}", oldRecord.rootDomainName());
                            pingDao.write(dnsPingService.pingDomain(oldRecord.rootDomainName(), oldRecord));
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Error processing DNS job for domain: " + job.reference(), e);
                }
                finally {
                    // Remove the domain from the processing map
                    processingDomainsDns.remove(job.reference());
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

    private void fetchNewPingJobs() {
        try {
            while (running) {

                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue; // re-fetch the records after resuming
                }

                List<DomainReference> domains = pingDao.getOrphanedDomains(nid);
                for (DomainReference domain : domains) {

                    if (nodeId == null) {
                        waitForResume();
                        break; // re-fetch the records after resuming
                    }

                    try {
                        pingJobQueue.put(new PingJob.Ping(domain));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Failed to add new ping job for domain: " + domain, e);
                    }
                }

                // This is an incredibly expensive operation, so we only do it once a day
                try {
                    TimeUnit.HOURS.sleep(24);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        catch (Exception e) {
            logger.error("Error fetching new ping jobs", e);
        }
    }

    private void fetchNewDnsRecords() {
        try {
            while (running) {
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue; // re-fetch the records after resuming
                }

                List<String> rootDomains = pingDao.getOrphanedRootDomains(nid);
                for (String rootDomain : rootDomains) {

                    if (nodeId == null) {
                        waitForResume();
                        break; // re-fetch the records after resuming
                    }

                    try {
                        dnsJobQueue.put(new DnsJob.DnsFetch(rootDomain));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Failed to add new DNS job for root domain: " + rootDomain, e);
                    }
                }
                // This is an incredibly expensive operation, so we only do it once a day
                TimeUnit.HOURS.sleep(24);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("DNS job fetch interrupted", e);
        }
    }

    private void updatePingJobs() {

        while (running) {
            try {
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue; // re-fetch the records after resuming
                }

                var statuses = pingDao.getNextDomainPingStatuses(100, nid);

                if (nodeId == null) {
                    waitForResume();
                    break; // re-fetch the records after resuming
                }

                for (var status : statuses) {
                    var job = switch (status) {
                        case HistoricalAvailabilityData.JustAvailability(String domain, DomainAvailabilityRecord record)
                                -> new PingJob.PingRefresh(domain, record, null);
                        case HistoricalAvailabilityData.AvailabilityAndSecurity(String domain, DomainAvailabilityRecord availability, DomainSecurityRecord security)
                                -> new PingJob.PingRefresh(domain, availability, security);
                    };

                    if (processingDomainsPing.putIfAbsent(job.reference(), true) == null) {
                        pingJobQueue.add(job);
                    }

                }
            }
            catch (Exception e) {
                logger.error("Error fetching next domain ping statuses", e);
            }
        }

    }

    private void updateDnsJobs() {
        while (running) {
            try {
                Integer nid = nodeId;
                if (nid == null) {
                    waitForResume();
                    continue; // re-fetch the records after resuming
                }

                var dnsRecords = pingDao.getNextDnsDomainRecords(1000, nid);
                for (var record : dnsRecords) {
                    if (nodeId == null) {
                        waitForResume();
                        break; // re-fetch the records after resuming
                    }
                    if (processingDomainsDns.putIfAbsent(record.rootDomainName(), true) == null) {
                        dnsJobQueue.put(new DnsJob.DnsRefresh(record));
                    }
                }
            }
            catch (Exception e) {
                logger.error("Error fetching next domain DNS records", e);
            }
        }
    }

}
