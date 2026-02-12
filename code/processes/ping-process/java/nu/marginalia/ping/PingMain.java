package nu.marginalia.ping;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.WmsaHome;
import nu.marginalia.coordination.DomainCoordinationModule;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.ping.PingRequest;
import nu.marginalia.ping.fetcher.PingDnsFetcher;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.time.Duration;
import java.time.Instant;

public class PingMain extends ProcessMainClass {
    private static final Logger log = LoggerFactory.getLogger(PingMain.class);

    private final PingJobScheduler pingJobScheduler;
    private final int node;

    private static final Logger logger = LoggerFactory.getLogger(PingMain.class);

    @Inject
    public PingMain(MessageQueueFactory messageQueueFactory,
                    ProcessConfiguration config,
                    Gson gson,
                    PingJobScheduler pingJobScheduler,
                    ProcessConfiguration processConfiguration
                    ) {
        super(messageQueueFactory, config, gson, ProcessInboxNames.PING_INBOX);

        this.pingJobScheduler = pingJobScheduler;
        this.node = processConfiguration.node();
    }

    public void run(Instant endTs) {

        if (Instant.now().isBefore(endTs)) {
            log.info("Starting PingMain...");
            pingJobScheduler.run(endTs);
            log.info("PingMain finished successfully.");
        }
        else {
            logger.info("Time slot aleady exceeded, termingating");
        }

    }

    public static void main(String... args) throws Exception {
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
                new PingModule(),
                new ServiceDiscoveryModule(),
                new DomainCoordinationModule(),
                new ProcessConfigurationModule("ping"),
                new DatabaseModule(false)
        );

        GeoIpDictionary geoIpDictionary = injector.getInstance(GeoIpDictionary.class);

        geoIpDictionary.waitReady(); // Ensure the GeoIpDictionary is ready before proceeding

        PingMain main = injector.getInstance(PingMain.class);

        var instructions = main.fetchInstructions(PingRequest.class);

        try {
            main.run(Instant.parse(instructions.value().endTs()));
            instructions.ok();
        }
        catch (Throwable ex) {
            logger.error("Error running ping process", ex);
            instructions.err();
        }
    }

}
