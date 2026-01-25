package nu.marginalia.service.server;

import io.jooby.*;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.mq.inbox.MqInboxIf;
import nu.marginalia.service.client.ServiceNotAvailableException;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.jte.JteModule;
import nu.marginalia.service.server.mq.ServiceMqSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JoobyService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker httpMarker = MarkerFactory.getMarker("HTTP");

    private final Initialization initialization;

    private final static Counter request_counter = Counter.builder().name("wmsa_request_counter").help("Request Counter")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_good = Counter.builder().name("wmsa_request_counter_good").help("Good Requests")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_bad = Counter.builder().name("wmsa_request_counter_bad").help("Bad Requests")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_err = Counter.builder().name("wmsa_request_counter_err").help("Error Requests")
            .labelNames("service", "node")
            .register();
    private final String serviceName;
    private static volatile boolean initialized = false;

    protected final MqInboxIf messageQueueInbox;
    private final int node;
    private GrpcServer grpcServer;

    private ServiceConfiguration config;
    private final List<MvcExtension> joobyServices;
    private final ServiceEndpoint restEndpoint;

    public JoobyService(BaseServiceParams params,
                        List<DiscoverableService> grpcServices,
                        List<MvcExtension> joobyServices
    ) throws Exception {

        this.joobyServices = joobyServices;
        this.initialization = params.initialization;
        config = params.configuration;
        node = config.node();

        String inboxName = config.serviceName();
        logger.info("Inbox name: {}", inboxName);

        var serviceRegistry = params.serviceRegistry;

        restEndpoint = serviceRegistry.registerService(ServiceKey.forRest(config.serviceId(), config.node()),
                config.instanceUuid(), config.externalAddress());

        var mqInboxFactory = params.messageQueueInboxFactory;
        messageQueueInbox = mqInboxFactory.createSynchronousInbox(inboxName, config.node(), config.instanceUuid());
        messageQueueInbox.subscribe(new ServiceMqSubscription(this));

        serviceName = System.getProperty("service-name");

        initialization.addCallback(params.heartbeat::start);
        initialization.addCallback(messageQueueInbox::start);
        initialization.addCallback(() -> params.eventLog.logEvent("SVC-INIT", serviceName + ":" + config.node()));
        initialization.addCallback(() -> serviceRegistry.announceInstance(config.instanceUuid()));

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (e instanceof ServiceNotAvailableException) {
                // reduce log spam for this common case
                logger.error("Service not available: {}", e.getMessage());
            }
            else {
                logger.error("Uncaught exception", e);
            }
            request_counter_err.labelValues(serviceName, Integer.toString(node)).inc();
        });

        if (!initialization.isReady() && ! initialized ) {
            initialized = true;
            grpcServer = new GrpcServer(config, serviceRegistry, grpcServices);
            grpcServer.start();
        }
    }

    public void startJooby(Jooby jooby) {

        logger.info("{} Listening to {}:{} ({})", getClass().getSimpleName(),
                restEndpoint.host(),
                restEndpoint.port(),
                config.externalAddress());

        // FIXME:  This won't work outside of docker, may need to submit a PR to jooby to allow classpaths here
        if (Files.exists(Path.of("/app/resources/jte")) || Files.exists(Path.of("/app/classes/jte-precompiled"))) {
            jooby.install(new JteModule(Path.of("/app/resources/jte"), Path.of("/app/classes/jte-precompiled")));
        }
        if (Files.exists(Path.of("/app/resources/static"))) {
            jooby.assets("/*", Paths.get("/app/resources/static"));
        }
        var options = new ServerOptions();
        options.setHost(config.bindAddress());
        options.setPort(restEndpoint.port());

        // Enable gzip compression of response data, but set compression to the lowest level
        // since it doesn't really save much more space to dial it up.  It's typically a
        // single digit percentage difference since HTML already compresses very well with level = 1.
        options.setCompressionLevel(1);

        // Set a cap on the number of worker and I/O threads, as Jooby's default value does not seem to consider
        // multi-tenant servers with high thread counts, and spins up an exorbitant number of threads in that
        // scenario
        options.setWorkerThreads(Math.min(16, options.getWorkerThreads()));
        options.setIoThreads(Math.min(16, options.getIoThreads()));

        jooby.setServerOptions(options);

        jooby.get("/internal/ping", ctx -> "pong");
        jooby.get("/internal/started", this::isInitialized);
        jooby.get("/internal/ready", this::isReady);

        for (var service : joobyServices) {
            jooby.mvc(service);
        }

        jooby.before(this::auditRequestIn);
        jooby.after(this::auditRequestOut);
    }

    private Object isInitialized(Context ctx) {
        if (initialization.isReady()) {
            return "ok";
        }
        else {
            ctx.setResponseCode(StatusCode.FAILED_DEPENDENCY_CODE);
            return "bad";
        }
    }

    public boolean isReady() {
        return true;
    }

    private String isReady(Context ctx) {
        if (isReady()) {
            return "ok";
        }
        else {
            ctx.setResponseCode(StatusCode.FAILED_DEPENDENCY_CODE);
            return "bad";
        }
    }

    private void auditRequestIn(Context ctx) {
        request_counter.labelValues(serviceName, Integer.toString(node)).inc();
    }

    private void auditRequestOut(Context ctx, Object result, Throwable failure) {
        if (ctx.getResponseCode().value() < 400) {
            request_counter_good.labelValues(serviceName, Integer.toString(node)).inc();
        }
        else {
            request_counter_bad.labelValues(serviceName, Integer.toString(node)).inc();
        }

        if (failure != null) {
            logger.error("Request failed " + ctx.getMethod() + " " + ctx.getRequestURL(), failure);
            request_counter_err.labelValues(serviceName, Integer.toString(node)).inc();
        }
    }

}