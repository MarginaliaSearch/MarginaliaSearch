package nu.marginalia.ndp;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.WmsaHome;
import nu.marginalia.coordination.DomainCoordinationModule;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.ndp.NdpRequest;
import nu.marginalia.ndp.model.DomainToTest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NdpMain extends ProcessMainClass {

    private static final Logger logger = LoggerFactory.getLogger(NdpMain.class);
    private final DomainNodeAllocator domainNodeAllocator;
    private final DomainTestingQueue domainTestingQueue;
    private final ProcessHeartbeat processHeartbeat;
    private final DomainEvaluator domainEvaluator;
    private final DomainBlacklist domainBlacklist;

    private final AtomicInteger domainCount = new AtomicInteger(0);

    @Inject
    public NdpMain(MessageQueueFactory messageQueueFactory,
                   ProcessConfiguration config,
                   DomainNodeAllocator domainNodeAllocator,
                   DomainTestingQueue domainTestingQueue,
                   DomainEvaluator domainEvaluator,
                   DomainBlacklist domainBlacklist,
                   ProcessHeartbeat processHeartbeat,
                   Gson gson)
    {
        super(messageQueueFactory, config, gson, ProcessInboxNames.NDP_INBOX);

        this.domainNodeAllocator = domainNodeAllocator;
        this.domainEvaluator = domainEvaluator;
        this.domainBlacklist = domainBlacklist;
        this.domainTestingQueue = domainTestingQueue;
        this.processHeartbeat = processHeartbeat;
    }


    public void run(int goalCount) throws InterruptedException {
        logger.info("Wait for blacklist to load...");
        domainBlacklist.waitUntilLoaded();

        SimpleBlockingThreadPool threadPool = new SimpleBlockingThreadPool(
                "NDP-Worker",
                8,
                10,
                SimpleBlockingThreadPool.ThreadType.PLATFORM
        );

        logger.info("Starting NDP process");

        int toInsertCount = goalCount - domainNodeAllocator.totalCount();

        if (toInsertCount <= 0) {
            logger.info("No new domains to process. Current count: " + domainNodeAllocator.totalCount());
            return;
        }

        try (var hb = processHeartbeat.createAdHocTaskHeartbeat("Growing Index")) {
            int cnt;
            while ((cnt = domainCount.get()) < toInsertCount) {
                if (cnt % 100 == 0) {
                    hb.progress("Discovered Domains", domainCount.get(), cnt);
                }

                var nextDomain = domainTestingQueue.next();
                threadPool.submit(() -> evaluateDomain(nextDomain));
            }
        }

        threadPool.shutDown();
        // Wait for all tasks to complete or give up after 1 hour
        threadPool.awaitTermination(1, TimeUnit.HOURS);

        logger.info("NDP process completed. Total domains processed: " + domainCount.get());

    }


    private void evaluateDomain(DomainToTest nextDomain) {
        try {
            if (domainEvaluator.evaluateDomain(nextDomain)) {
                logger.info("Accepting: {}", nextDomain.domainName());
                domainCount.incrementAndGet();
                domainTestingQueue.accept(nextDomain, domainNodeAllocator.nextNodeId());
            } else {
                logger.info("Rejecting: {}", nextDomain.domainName());
                domainTestingQueue.reject(nextDomain);
            }
        }
        catch (Exception e) {
            domainTestingQueue.reject(nextDomain);
            logger.error("Error evaluating domain: " + nextDomain.domainId(), e);
        }
    }

    public static void main(String[] args) throws Exception {
        // Prevent Java from caching DNS lookups forever (filling up the system RAM as a result)
        Security.setProperty("networkaddress.cache.ttl" , "3600");

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        // Set the maximum number of connections to keep alive in the connection pool
        System.setProperty("jdk.httpclient.idleTimeout", "15"); // 15 seconds
        System.setProperty("jdk.httpclient.connectionPoolSize", "256");

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");


        Injector injector = Guice.createInjector(
                new NdpModule(),
                new ServiceDiscoveryModule(),
                new DomainCoordinationModule(),
                new ProcessConfigurationModule("ndp"),
                new DatabaseModule(false)
        );

        GeoIpDictionary geoIpDictionary = injector.getInstance(GeoIpDictionary.class);

        geoIpDictionary.waitReady(); // Ensure the GeoIpDictionary is ready before proceeding

        NdpMain main = injector.getInstance(NdpMain.class);

        var instructions = main.fetchInstructions(NdpRequest.class);

        try {
            main.run(instructions.value().goal());
            instructions.ok();
        }
        catch (Throwable ex) {
            logger.error("Error running ping process", ex);
            instructions.err();
        }
    }
}
