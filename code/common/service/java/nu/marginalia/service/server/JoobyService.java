package nu.marginalia.service.server;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import io.grpc.BindableService;
import io.jooby.*;
import io.jooby.handlebars.HandlebarsModule;
import io.prometheus.client.Counter;
import lombok.SneakyThrows;
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

import java.util.List;

public class JoobyService {
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

    private ServiceConfiguration config;
    private final List<MvcExtension> joobyServices;
    private final ServiceEndpoint restEndpoint;

    @SneakyThrows
    public JoobyService(BaseServiceParams params,
                        ServicePartition partition,
                        Initialization initialization,
                        List<BindableService> grpcServices,
                        List<MvcExtension> joobyServices
                        ) {

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
            request_counter_err.labels(serviceName, Integer.toString(node)).inc();
        });

        if (!initialization.isReady() && ! initialized ) {
            initialized = true;
            grpcServer = new GrpcServer(config, serviceRegistry, partition, grpcServices);
            grpcServer.start();
        }
    }

    public void startJooby(Jooby jooby) {

        logger.info("{} Listening to {}:{} ({})", getClass().getSimpleName(),
                restEndpoint.host(),
                restEndpoint.port(),
                config.externalAddress());

        TemplateLoader loader = new ClassPathTemplateLoader();

        loader.setPrefix("/templates");
        loader.setSuffix("");

        var handlebars = new Handlebars(loader);
        handlebars.registerHelpers(ConditionalHelpers.class);
        jooby.install(new HandlebarsModule(handlebars));

        var options = new ServerOptions();
        options.setHost(config.bindAddress());
        options.setPort(restEndpoint.port());
        jooby.setServerOptions(options);

        jooby.get("/internal/ping", ctx -> "pong");
        jooby.get("/internal/started", this::isInitialized);
        jooby.get("/internal/ready", this::isReady);

        for (var service : joobyServices) {
            jooby.mvc(service);
        }

        jooby.assets("/*", "/static");

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
        request_counter.labels(serviceName, Integer.toString(node)).inc();
    }

    private void auditRequestOut(Context ctx, Object result, Throwable failure) {
        if (ctx.getResponseCode().value() < 400) {
            request_counter_good.labels(serviceName, Integer.toString(node)).inc();
        }
        else {
            request_counter_bad.labels(serviceName, Integer.toString(node)).inc();
        }

        if (failure != null) {
            logger.error("Request failed " + ctx.getMethod() + " " + ctx.getRequestURL(), failure);
            request_counter_err.labels(serviceName, Integer.toString(node)).inc();
        }
    }

}
