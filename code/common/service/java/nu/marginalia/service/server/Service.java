package nu.marginalia.service.server;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import io.prometheus.client.Counter;
import lombok.SneakyThrows;
import nu.marginalia.mq.inbox.*;
import nu.marginalia.service.NamedExecutorFactory;
import nu.marginalia.service.client.ServiceNotAvailableException;
import nu.marginalia.service.discovery.property.*;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.server.mq.ServiceMqSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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

    @SneakyThrows
    public Service(BaseServiceParams params,
                   Runnable configureStaticFiles,
                   ServicePartition partition,
                   List<BindableService> grpcServices) {

        this.initialization = params.initialization;
        var config = params.configuration;
        node = config.node();

        if (config.serviceId() == ServiceId.Control) {
            // Special case for first boot, since the control service
            // owns database migrations and so on, we need other processes
            // to wait for this to be done before they start.  This is
            // only needed once.
            params.serviceRegistry.declareFirstBoot();
        }
        else {
            params.serviceRegistry.waitForFirstBoot();
        }

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
            Spark.before(this::filterPublicRequests);
            Spark.after(this::auditRequestOut);

            // Live and ready endpoints
            Spark.get("/internal/ping", (rq,rp) -> "pong");
            Spark.get("/internal/started", this::isInitialized);
            Spark.get("/internal/ready", this::isReady);

            int port = params.serviceRegistry.requestPort(config.externalAddress(), new ServiceKey.Grpc<>("-", partition));


            int nThreads = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 16);

            // Start the gRPC server
            var grpcServerBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(config.bindAddress(), port))
                    .executor(NamedExecutorFactory.createFixed("nettyExecutor", nThreads))
                    .workerEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Worker-ELG", nThreads)))
                    .bossEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Boss-ELG", nThreads)))
                    .channelType(NioServerSocketChannel.class);

            for (var grpcService : grpcServices) {
                var svc = grpcService.bindService();

                params.serviceRegistry.registerService(
                        ServiceKey.forServiceDescriptor(svc.getServiceDescriptor(), partition),
                        config.instanceUuid(),
                        config.externalAddress()
                        );

                grpcServerBuilder.addService(svc);
            }
            grpcServerBuilder.build().start();
        }
    }

    public Service(BaseServiceParams params,
                   ServicePartition partition,
                   List<BindableService> grpcServices) {
        this(params,
                Service::defaultSparkConfig,
                partition,
                grpcServices);
    }

    public Service(BaseServiceParams params) {
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

    private void filterPublicRequests(Request request, Response response) {
        if (null == request.headers("X-Public")) {
            return;
        }

        String context = Optional
                        .ofNullable(request.headers("X-Context"))
                        .orElseGet(request::ip);

        if (!request.pathInfo().startsWith("/public/")) {
            logger.warn(httpMarker, "External connection to internal API: {} -> {} {}", context, request.requestMethod(), request.pathInfo());
            Spark.halt(403);
        }

        logRequest(request);
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
