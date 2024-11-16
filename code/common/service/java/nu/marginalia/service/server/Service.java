package nu.marginalia.service.server;

import io.prometheus.client.Counter;
import nu.marginalia.mq.inbox.MqInboxIf;
import nu.marginalia.service.client.ServiceNotAvailableException;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.mq.ServiceMqSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.List;

public class Service {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker httpMarker = MarkerFactory.getMarker("HTTP");

    private final Initialization initialization;

    private final static Counter request_counter = Counter.build("wmsa_request_counter", "Request Counter")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_good = Counter.build("wmsa_request_counter_good", "Good Requests")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_bad = Counter.build("wmsa_request_counter_bad", "Bad Requests")
            .labelNames("service", "node")
            .register();
    private final static Counter request_counter_err = Counter.build("wmsa_request_counter_err", "Error Requests")
            .labelNames("service", "node")
            .register();
    private final String serviceName;
    private static volatile boolean initialized = false;

    protected final MqInboxIf messageQueueInbox;
    private final int node;
    private GrpcServer grpcServer;

    public Service(BaseServiceParams params,
                   Runnable configureStaticFiles,
                   ServicePartition partition,
                   List<DiscoverableService> grpcServices) throws Exception {

        this.initialization = params.initialization;
        var config = params.configuration;
        node = config.node();

        String inboxName = config.serviceName();
        logger.info("Inbox name: {}", inboxName);

        var serviceRegistry = params.serviceRegistry;

        var restEndpoint =
                serviceRegistry.registerService(
                        ServiceKey.forRest(config.serviceId(), config.node()),
                    config.instanceUuid(),
                    config.externalAddress()
                );

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
            request_counter_err.labels(serviceName, Integer.toString(node)).inc();
        });

        if (!initialization.isReady() && ! initialized ) {
            initialized = true;

            Spark.threadPool(32, 4, 60_000);

            Spark.ipAddress(config.bindAddress());
            Spark.port(restEndpoint.port());

            logger.info("{} Listening to {}:{} ({})", getClass().getSimpleName(),
                    params.configuration.bindAddress(),
                    restEndpoint.port(),
                    params.configuration.externalAddress());

            configureStaticFiles.run();

            Spark.before(this::auditRequestIn);
            Spark.after(this::auditRequestOut);

            // Live and ready endpoints
            Spark.get("/internal/ping", (rq,rp) -> "pong");
            Spark.get("/internal/started", this::isInitialized);
            Spark.get("/internal/ready", this::isReady);

            Spark.get("/public/", (rq, rp) -> {
                rp.type("text/html");

                return """
                        <html><body>
                        <h1>Migration required</h1>
                        <p>The system is configured to use an old URL scheme.  If you are the operator of the service,
                        you need to modify the reverse proxy to not use the /public prefix.  If you are a user, please
                        contact the operator of the service.</p>
                        """;
            });

            grpcServer = new GrpcServer(config, serviceRegistry, partition, grpcServices);
            grpcServer.start();
        }
    }

    public Service(BaseServiceParams params,
                   ServicePartition partition,
                   List<DiscoverableService> grpcServices) throws Exception {
        this(params,
                Service::defaultSparkConfig,
                partition,
                grpcServices);
    }

    public Service(BaseServiceParams params) throws Exception {
        this(params,
                Service::defaultSparkConfig,
                ServicePartition.any(),
                List.of());
    }

    private static void defaultSparkConfig() {
        // configureStaticFiles can't be an overridable method in Service because it may
        // need to depend on parameters to the constructor, and super-constructors
        // must run first
        Spark.staticFiles.expireTime(3600);
        Spark.staticFiles.header("Cache-control", "public");
    }

    private Object isInitialized(Request request, Response response) {
        if (initialization.isReady()) {
            return "ok";
        }
        else {
            response.status(424);
            return "bad";
        }
    }

    public boolean isReady() {
        return true;
    }

    private String isReady(Request request, Response response) {
        if (isReady()) {
            return "ok";
        }
        else {
            response.status(424);
            return "bad";
        }
    }

    private void auditRequestIn(Request request, Response response) {
        request_counter.labels(serviceName, Integer.toString(node)).inc();
    }

    private void auditRequestOut(Request request, Response response) {
        if (response.status() < 400) {
            request_counter_good.labels(serviceName, Integer.toString(node)).inc();
        }
        else {
            request_counter_bad.labels(serviceName, Integer.toString(node)).inc();
        }

        logResponse(request, response);

    }

    /** Log the request on the HTTP log */
    protected void logRequest(Request request) {
        String url = request.pathInfo();
        if (request.queryString() != null) {
            url = url + "?" + request.queryString();
        }

        logger.info(httpMarker, "PUBLIC: {} {}", request.requestMethod(), url);
    }

    /** Log the response on the HTTP log */
    protected void logResponse(Request request, Response response) {
        if (null != request.headers("X-Public")) {
            logger.info(httpMarker, "RSP {}", response.status());
        }
    }

}
