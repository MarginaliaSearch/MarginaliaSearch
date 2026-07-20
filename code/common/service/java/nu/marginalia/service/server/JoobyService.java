package nu.marginalia.service.server;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.jooby.*;
import io.jooby.exception.MethodNotAllowedException;
import io.jooby.handler.AssetSource;
import io.jooby.jte.JteModule;
import io.jooby.netty.NettyServer;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.mq.inbox.MqInboxIf;
import nu.marginalia.service.client.ServiceNotAvailableException;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.mq.ServiceMqSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
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
    private final List<Extension> joobyServices;
    private final ServiceEndpoint restEndpoint;

    public JoobyService(BaseServiceParams params,
                        List<DiscoverableService> grpcServices,
                        List<Extension> joobyServices
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

    /** Build the HTTP server with options derived from the service configuration
     * for Jooby to use.
     */
    public Server createServer() {
        var options = new ServerOptions();
        options.setHost(config.bindAddress());
        options.setPort(restEndpoint.port());

        // docker-specific kludge to allow the rest endpoint to be discovered from the health check,
        // which is otherwise not possible since we have to bind to a specific internal interface on
        // ipvlan configurations to avoid public access to the internal APIs.
        if (Files.isDirectory(Path.of("/app"))) {
            try {
                String uriBase = "http://" + restEndpoint.host() + ":" + restEndpoint.port();
                Files.writeString(Path.of("/tmp/rest-addr"), uriBase);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Enable gzip compression of response data, but set compression to the lowest level
        // since it doesn't really save much more space to dial it up.  It's typically a
        // single digit percentage difference since HTML already compresses very well with level = 1.
        options.setCompressionLevel(1);

        // Set a cap on the number of worker and I/O threads, as Jooby's default value does not seem to consider
        // multi-tenant servers with high thread counts, and spins up an exorbitant number of threads in that
        // scenario
        options.setWorkerThreads(Math.min(16, options.getWorkerThreads()));
        options.setIoThreads(Math.min(16, options.getIoThreads()));

        return new NettyServer(options);
    }

    public void startJooby(Jooby jooby) {

        logger.info("{} Listening to {}:{} ({})", getClass().getSimpleName(),
                restEndpoint.host(),
                restEndpoint.port(),
                config.externalAddress());

        configureStaticResources(jooby);
        configureErrorHandling(jooby);

        jooby.get("/internal/ping", ctx -> "pong");
        jooby.get("/internal/started", this::isInitialized);
        jooby.get("/internal/ready", this::isReady);

        for (var service : joobyServices) {
            jooby.mvc(service);
        }

        jooby.before(this::auditRequestIn);
        jooby.after(this::auditRequestOut);
    }

    public static void configureErrorHandling(Jooby jooby) {
        jooby.error(MalformedInputException.class, (ctx, cause, code) -> {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            ctx.setResponseType(MediaType.TEXT);
            ctx.send("Bad request");
        });

        jooby.error(MethodNotAllowedException.class, (ctx, cause, code) -> {
            ctx.setResponseCode(StatusCode.METHOD_NOT_ALLOWED);
            ctx.setResponseType(MediaType.TEXT);
            ctx.send("Method not allowed");
        });

        jooby.error(StatusRuntimeException.class, (ctx, cause, code) -> {
            var sre = (StatusRuntimeException) cause;

            switch (sre.getStatus().getCode()) {
                case Status.Code.RESOURCE_EXHAUSTED -> {
                    ctx.setResponseCode(StatusCode.FAILED_DEPENDENCY);
                    ctx.setResponseType(MediaType.TEXT);
                    ctx.send("Service overloaded");
                }
                case Status.Code.UNAVAILABLE -> {
                    ctx.setResponseCode(StatusCode.SERVICE_UNAVAILABLE);
                    ctx.setResponseType(MediaType.TEXT);
                    ctx.send("Service unavailable");
                }
                case Status.Code.FAILED_PRECONDITION -> {
                    ctx.setResponseCode(StatusCode.BAD_REQUEST);
                    ctx.setResponseType(MediaType.TEXT);
                    ctx.send("Bad request");
                }
                default -> {
                    ctx.setResponseCode(StatusCode.SERVER_ERROR);
                    ctx.setResponseType(MediaType.TEXT);
                    ctx.send("Server error");
                }
            }
        });
    }

    /** Set up serving of jte templates and static resources.  In docker, the jib image build
     * exposes an exploded directory layout under /app, and the files are served directly off
     * the filesystem.  Outside of docker, the templates are precompiled into the service jar
     * by the jte gradle plugin, and the static files are served from the classpath.
     */
    public static void configureStaticResources(Jooby jooby) {
        if (Files.exists(Path.of("/app/resources/jte")) || Files.exists(Path.of("/app/classes"))) {
            jooby.install(new JteModule(Path.of("/app/resources/jte"), Path.of("/app/classes")));
        }
        else if (JoobyService.class.getResource("/gg/jte/generated/precompiled") != null) {
            jooby.install(new JteModule(TemplateEngine.createPrecompiled(ContentType.Html)));
        }

        if (Files.exists(Path.of("/app/resources/static"))) {
            jooby.assets("/*", Paths.get("/app/resources/static"));
        }
        else if (JoobyService.class.getResource("/static") != null) {
            jooby.assets("/*", AssetSource.create(JoobyService.class.getClassLoader(), "/static"));
        }
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
            request_counter_err.labelValues(serviceName, Integer.toString(node)).inc();
        }
    }

}