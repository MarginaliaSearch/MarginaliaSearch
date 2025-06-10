package nu.marginalia.ping;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.WmsaHome;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.ping.PingRequest;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.List;

public class PingMain extends ProcessMainClass {
    private static final Logger log = LoggerFactory.getLogger(PingMain.class);

    private final PingJobScheduler pingJobScheduler;
    private final ServiceRegistryIf serviceRegistry;
    private final NodeConfigurationService nodeConfigurationService;
    private final int node;

    private static final Logger logger = LoggerFactory.getLogger(PingMain.class);

    @Inject
    public PingMain(MessageQueueFactory messageQueueFactory,
                    ProcessConfiguration config,
                    Gson gson,
                    PingJobScheduler pingJobScheduler,
                    ServiceRegistryIf serviceRegistry,
                    NodeConfigurationService nodeConfigurationService,
                    ProcessConfiguration processConfiguration
                    ) {
        super(messageQueueFactory, config, gson, ProcessInboxNames.PING_INBOX);

        this.pingJobScheduler = pingJobScheduler;
        this.serviceRegistry = serviceRegistry;
        this.nodeConfigurationService = nodeConfigurationService;
        this.node = processConfiguration.node();
    }

    public void runPrimary() {
        log.info("Starting PingMain...");

        // Start the ping job scheduler
        pingJobScheduler.start(true);

        // Watch the crawler process to suspend/resume the ping job scheduler
        try {
            serviceRegistry.watchProcess("crawler", node, (running) -> {
                if (running) {
                    log.info("Crawler process is running, suspending ping job scheduler.");
                    pingJobScheduler.pause(node);
                } else {
                    log.warn("Crawler process is not running, resuming ping job scheduler.");
                    pingJobScheduler.resume(node);
                }
            });
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to watch crawler process", e);
        }

        log.info("PingMain started successfully.");
    }


    public void runSecondary() {
        log.info("Starting PingMain...");

        List<Integer> crawlerNodes = nodeConfigurationService.getAll()
                .stream()
                .filter(node -> !node.disabled())
                .filter(node -> node.profile().permitBatchCrawl())
                .map(NodeConfiguration::node)
                .toList()
                ;

        // Start the ping job scheduler
        pingJobScheduler.start(true);

        // Watch the crawler process to suspend/resume the ping job scheduler
        try {
            serviceRegistry.watchProcessAnyNode("crawler", crawlerNodes, (running, n) -> {
                if (running) {
                    log.info("Crawler process is running on node {} taking over ", n);
                    pingJobScheduler.resume(n);
                } else {
                    log.warn("Crawler process stopped, resuming ping job scheduler.");
                    pingJobScheduler.pause(n);
                }
            });
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to watch crawler process", e);
        }

        log.info("PingMain started successfully.");
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
                new ProcessConfigurationModule("ping"),
                new DatabaseModule(false)
        );

        GeoIpDictionary geoIpDictionary = injector.getInstance(GeoIpDictionary.class);

        geoIpDictionary.waitReady(); // Ensure the GeoIpDictionary is ready before proceeding

        PingMain main = injector.getInstance(PingMain.class);

        var instructions = main.fetchInstructions(PingRequest.class);

        try {
            switch (instructions.value().runClass) {
                case "primary":
                    log.info("Running as primary node");
                    main.runPrimary();
                    break;
                case "secondary":
                    log.info("Running as secondary node");
                    main.runSecondary();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid runClass: " + instructions.value().runClass);
            }
            for(;;);
        }
        catch (Throwable ex) {
            logger.error("Error running ping process", ex);
            instructions.err();
        }
    }

}
