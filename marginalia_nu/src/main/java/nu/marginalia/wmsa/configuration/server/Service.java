package nu.marginalia.wmsa.configuration.server;

import com.google.common.base.Strings;
import io.prometheus.client.Counter;
import nu.marginalia.wmsa.client.exception.MessagingException;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.ThreadContext;
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

    public Service(String ip, int port, Initialization initialization, MetricsServer metricsServer) {
        this.initialization = initialization;

        serviceName = System.getProperty("service-name");

        if (!initialization.isReady() && ! initialized ) {
            initialized = true;

            Spark.threadPool(32, 4, 60_000);
            Spark.ipAddress(ip);
            Spark.port(port);

            logger.info("{} Listening to {}:{}", getClass().getSimpleName(), ip == null ? "" : ip, port);

            Spark.staticFiles.expireTime(3600);
            Spark.staticFiles.header("Cache-control", "public");

            Spark.before(this::filterPublicRequests);
            Spark.before(this::auditRequestIn);
            Spark.after(this::auditRequestOut);
            Spark.exception(MessagingException.class, this::handleException);

            Spark.get("/internal/ping", (rq,rp) -> "pong");
            Spark.get("/internal/started", this::isInitialized);
            Spark.get("/internal/ready", this::isReady);
            Spark.get("/public/who", (rq,rp) -> getClass().getSimpleName());
        }
    }

    private void filterPublicRequests(Request request, Response response) {
        if (null != request.headers("X-Public")) {

            String context = Optional
                            .ofNullable(request.headers("X-Context"))
                            .orElseGet(request::ip);

            if (!request.pathInfo().startsWith("/public/")) {
                logger.warn(httpMarker, "External connection to internal API: {} -> {} {}", context, request.requestMethod(), request.pathInfo());
                Spark.halt(HttpStatus.SC_FORBIDDEN);
            }

            String url = request.pathInfo();
            if (request.queryString() != null) {
                url = url + "?" + request.queryString();
            }
            logger.info(httpMarker, "PUBLIC {}: {} {}", Context.fromRequest(request).getIpHash().orElse("?"), request.requestMethod(), url);
        }
    }

    private Object isInitialized(Request request, Response response) {
        if (initialization.isReady()) {
            return "ok";
        }
        else {
            response.status(HttpStatus.SC_FAILED_DEPENDENCY);
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
            response.status(HttpStatus.SC_FAILED_DEPENDENCY);
            return "bad";
        }
    }

    private void auditRequestIn(Request request, Response response) {
        request_counter.labels(serviceName).inc();

        // Paint context
        if (!Strings.isNullOrEmpty(request.headers(Context.CONTEXT_HEADER))) {
            Context.fromRequest(request);
        }
    }
    private void auditRequestOut(Request request, Response response) {
        ThreadContext.clearMap();

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
