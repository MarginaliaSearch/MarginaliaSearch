package nu.marginalia.service.server;

import io.prometheus.client.Counter;
import nu.marginalia.client.Context;
import nu.marginalia.client.exception.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Optional;

public class Service {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker httpMarker = MarkerFactory.getMarker("HTTP");

    private final Initialization initialization;

    private final static Counter request_counter = Counter.build("wmsa_service_in_request_counter", "Request Counter")
            .labelNames("service")
            .register();
    private final static Counter request_counter_good = Counter.build("wmsa_service_good_request_counter", "Good Requests")
            .labelNames("service")
            .register();
    private final static Counter request_counter_bad = Counter.build("wmsa_service_bad_request_counter", "Bad Requests")
            .labelNames("service")
            .register();
    private final static Counter request_counter_err = Counter.build("wmsa_service_error_request_counter", "Error Requests")
            .labelNames("service")
            .register();
    private final String serviceName;
    private static volatile boolean initialized = false;

    public Service(BaseServiceParams params,
                   Runnable configureStaticFiles
                   ) {
        this.initialization = params.initialization;

        serviceName = System.getProperty("service-name");

        initialization.addCallback(params.heartbeat::start);
        initialization.addCallback(() -> params.eventLog.logEvent("SVC-INIT", ""));

        if (!initialization.isReady() && ! initialized ) {
            initialized = true;

            Spark.threadPool(32, 4, 60_000);
            Spark.ipAddress(params.configuration.host());
            Spark.port(params.configuration.port());

            logger.info("{} Listening to {}:{}", getClass().getSimpleName(),
                    params.configuration.host(),
                    params.configuration.port());

            configureStaticFiles.run();

            Spark.before(this::auditRequestIn);
            Spark.before(this::filterPublicRequests);
            Spark.after(this::auditRequestOut);
            Spark.exception(MessagingException.class, this::handleException);

            Spark.get("/internal/ping", (rq,rp) -> "pong");
            Spark.get("/internal/started", this::isInitialized);
            Spark.get("/internal/ready", this::isReady);
            Spark.get("/public/who", (rq,rp) -> getClass().getSimpleName());
        }
    }

    public Service(BaseServiceParams params) {
        this(params, () -> {
            // configureStaticFiles can't be an overridable method in Service because it may
            // need to depend on parameters to the constructor, and super-constructors
            // must run first
            Spark.staticFiles.expireTime(3600);
            Spark.staticFiles.header("Cache-control", "public");
        });
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

        String url = request.pathInfo();
        if (request.queryString() != null) {
            url = url + "?" + request.queryString();
        }
        logger.info(httpMarker, "PUBLIC {}: {} {}", Context.fromRequest(request).getContextId(), request.requestMethod(), url);
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
        // Paint context
        paintThreadName(request, "req:");

        request_counter.labels(serviceName).inc();
    }

    private void auditRequestOut(Request request, Response response) {

        paintThreadName(request, "rsp:");

        if (response.status() < 400) {
            request_counter_good.labels(serviceName).inc();
        }
        else {
            request_counter_bad.labels(serviceName).inc();
        }

        if (null != request.headers("X-Public")) {
            logger.info(httpMarker, "RSP {}", response.status());
        }
    }

    private void paintThreadName(Request request, String prefix) {
        var ctx = Context.fromRequest(request);
        Thread.currentThread().setName(prefix + ctx.getContextId());
    }

    private void handleException(Exception ex, Request request, Response response) {
        request_counter_err.labels(serviceName).inc();
        if (ex instanceof MessagingException) {
            logger.error("{} {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        else {
            logger.error("Uncaught exception", ex);
        }
    }

}
