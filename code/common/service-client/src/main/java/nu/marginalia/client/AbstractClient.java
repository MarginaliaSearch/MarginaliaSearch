package nu.marginalia.client;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessageV3;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.client.exception.LocalException;
import nu.marginalia.client.exception.NetworkException;
import nu.marginalia.client.exception.RemoteException;
import nu.marginalia.client.exception.RouteNotConfiguredException;
import nu.marginalia.client.model.ClientRoute;
import okhttp3.*;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractClient implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String CONTEXT_OUTBOUND_REQUEST = "outbound-request";
    private final Gson gson;
    private final OkHttpClient client;

    private boolean quiet;
    private final Map<Integer, String> serviceRoutes;
    private int timeout;

    private final LivenessMonitor livenessMonitor = new LivenessMonitor();
    private final Thread livenessMonitorThread;

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public AbstractClient(ClientRoute route, int timeout, Supplier<Gson> gsonProvider) {
        this(Map.of(0, route), timeout, gsonProvider);
    }

    public AbstractClient(Map<Integer, ClientRoute> routes,
                          int timeout,
                          Supplier<Gson> gsonProvider)
    {
        routes.forEach((node, route) -> {
            logger.info("Creating client route for {}:{} -> {}:{}", getClass().getSimpleName(), node, route.host(), route.port());
        });

        this.gson = gsonProvider.get();

        this.timeout = timeout;
        client = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(6000, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();

        serviceRoutes = new HashMap<>(routes.size());

        routes.forEach((node, client) ->
                serviceRoutes.put(node, new HttpHost(client.host(), client.port()).toURI())
        );

        RxJavaPlugins.setErrorHandler(e -> {
            if (e.getMessage() == null) {
                logger.error("Error", e);
            }
            else {
                logger.error("Error {}: {}", e.getClass().getSimpleName(), e.getMessage());
            }
        });

        livenessMonitorThread = new Thread(livenessMonitor, getClass().getSimpleName() + "-monitor");
        livenessMonitorThread.setDaemon(true);
        livenessMonitorThread.start();

        logger.info("Finished creating client for {}", getClass().getSimpleName());
    }

    private class LivenessMonitor implements Runnable {
        private final ConcurrentHashMap<Integer, Boolean> alivenessMap = new ConcurrentHashMap<>();

        @SneakyThrows
        public void run() {
            Thread.sleep(100); // Wait for initialization
            try {
                for (; ; ) {
                    boolean allAlive = true;
                    try {
                        for (int node : serviceRoutes.keySet()) {
                            boolean isResponsive = isResponsive(node);
                            alivenessMap.put(node, isResponsive);
                            allAlive &= !isResponsive;
                        }
                    }
                    //
                    catch (Exception ex) {
                        logger.warn("Oops", ex);
                    }
                    if (allAlive) {
                        synchronized (this) {
                            wait(1000);
                        }
                    }
                    else {
                        Thread.sleep(100);
                    }
                }
            } catch (InterruptedException ex) {
                // nothing to see here
            }
        }

        public boolean isAlive(int node) {
            return alivenessMap.getOrDefault(node, false);
        }

        public synchronized boolean isResponsive(int node) {
            Context ctx = Context.internal("ping");
            var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + "/internal/ping").get().build();

            return Observable.just(client.newCall(req))
                    .subscribeOn(scheduler().get())
                    .map(Call::execute)
                    .map(AbstractClient.this::getResponseStatus)
                    .flatMap(line -> validateStatus(line, req).timeout(5000, TimeUnit.SECONDS).onErrorReturn(e -> 500))
                    .onErrorReturn(error -> 500)
                    .map(HttpStatusCode::new)
                    .map(HttpStatusCode::isGood)
                    .blockingFirst();
        }
    }

    @Override
    public void close() {
        livenessMonitorThread.interrupt();
        scheduler().close();
    }

    public abstract AbortingScheduler scheduler();

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public abstract String name();


    public synchronized boolean isAccepting() {
        Context ctx = Context.internal("ready");

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(0) + "/internal/ready").get().build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(Call::execute)
                .map(this::getResponseStatus)
                .flatMap(line -> validateStatus(line, req))
                .timeout(100, TimeUnit.MILLISECONDS)
                .onErrorReturn(error -> 500)
                .map(HttpStatusCode::new)
                .map(HttpStatusCode::isGood)
                .blockingFirst();
    }

    @SneakyThrows
    protected synchronized Observable<HttpStatusCode> post(Context ctx,
                                                           int node,
                                                           String endpoint,
                                                           Object data) {

        ensureAlive(node);

        RequestBody body = RequestBody.create(json(data), MediaType.parse("application/json; charset=utf-8"));

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).post(body).build();

        return Observable
                .just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(this::getResponseStatus)
                .retryWhen(this::retryHandler)
                .flatMap(line -> validateStatus(line, req))
                .map(HttpStatusCode::new)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    @SneakyThrows
    protected synchronized Observable<HttpStatusCode> post(Context ctx, int node, String endpoint, GeneratedMessageV3 data) {

        ensureAlive(0);

        RequestBody body = RequestBody.create(data.toByteArray(), MediaType.parse("application/protobuf"));

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).post(body).build();
        var call = client.newCall(req);

        logInbound(call);
        ThreadContext.put("outbound-request", serviceRoutes.get(node) + endpoint);
        try (var rsp = call.execute()) {
            logOutbound(rsp);
            int code = rsp.code();

            return validateStatus(code, req).map(HttpStatusCode::new);
        }
        finally {
            ThreadContext.remove("outbound-request");
        }
    }


    @SneakyThrows
    protected synchronized <T> Observable<T> postGet(Context ctx, int node, String endpoint, Object data, Class<T> returnType) {

        ensureAlive(0);

        RequestBody body = RequestBody.create(json(data), MediaType.parse("application/json"));
        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).post(body).build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .retryWhen(this::retryHandler)
                .map(rsp -> validateResponseStatus(rsp, req, 200))
                .map(rsp -> getEntity(rsp, returnType))
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    protected synchronized Observable<HttpStatusCode> post(Context ctx, int node, String endpoint, String data, MediaType mediaType) {
        ensureAlive(0);

        var body = RequestBody.create(data, mediaType);

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).post(body).build();
        var call = client.newCall(req);


        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put(CONTEXT_OUTBOUND_REQUEST, serviceRoutes.get(node) + endpoint);
                    return c;
                })
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(this::getResponseStatus)
                .retryWhen(this::retryHandler)
                .flatMap(line -> validateStatus(line, req))
                .map(HttpStatusCode::new)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    protected synchronized <T> Observable<T> get(Context ctx, int node, String endpoint, Class<T> type) {
        ensureAlive(0);

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).get().build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(rsp -> validateResponseStatus(rsp, req, 200))
                .map(rsp -> getEntity(rsp, type))
                .retryWhen(this::retryHandler)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    @SuppressWarnings("unchecked")
    protected synchronized Observable<String> get(Context ctx, int node, String endpoint) {
        ensureAlive(0);

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).get().build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(rsp -> validateResponseStatus(rsp, req,200))
                .map(this::getText)
                .retryWhen(this::retryHandler)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    protected synchronized Observable<HttpStatusCode> delete(Context ctx, int node, String endpoint) {
        ensureAlive(0);

        var req = ctx.paint(new Request.Builder()).url(serviceRoutes.get(node) + endpoint).delete().build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(this::getResponseStatus)
                .flatMap(line -> validateStatus(line, req))
                .map(HttpStatusCode::new)
                .retryWhen(this::retryHandler)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }


    @SneakyThrows
    private Call logInbound(Call outgoing) {
        return outgoing;
    }

    @SneakyThrows
    private Response logOutbound(Response incoming) {
        return incoming;
    }

    @SneakyThrows
    private void ensureAlive(int node) {
        if (!isAlive(node)) {
            wait(2000);
            if (!isAlive(node)) {
                throw new RouteNotConfiguredException("Route not configured for " + name() + " -- tried " + serviceRoutes.get(node));
            }
        }
    }


    private ObservableSource<?> retryHandler(Observable<Throwable> error) {
        return error.flatMap(this::filterRetryableExceptions);
    }

    private Observable<Throwable> filterRetryableExceptions(Throwable error) throws Throwable {

        synchronized (livenessMonitor) {
            // Signal to the liveness monitor that we may have an outage
            livenessMonitor.notifyAll();
        }

        if (error.getClass().equals(RouteNotConfiguredException.class)) {
            logger.error("Network error {}", error.getMessage());
            return Observable.<Throwable>empty().delay(50, TimeUnit.MILLISECONDS);
        }
        else if (error.getClass().equals(NetworkException.class)) {
            logger.error("Network error {}", error.getMessage());
            return Observable.<Throwable>empty().delay(1, TimeUnit.SECONDS);
        }
        else if (error.getClass().equals(ConnectException.class)) {
            logger.error("Network error {}", error.getMessage());
            return Observable.<Throwable>empty().delay(1, TimeUnit.SECONDS);
        }

        if (!quiet) {
            if (error.getMessage() != null) {
                logger.error("{} {}", error.getClass().getSimpleName(), error.getMessage());
            }
            else {
                logger.error("Error ", error);
            }
        }
        throw error;
    }

    private Observable<Integer> validateStatus(int status, Request request) {
        if (status == org.apache.http.HttpStatus.SC_OK)
            return Observable.just(status);
        if (status == org.apache.http.HttpStatus.SC_ACCEPTED)
            return Observable.just(status);
        if (status == org.apache.http.HttpStatus.SC_CREATED)
            return Observable.just(status);

        return Observable.error(new RemoteException(name() + " responded status code " + status + " " + request.url()));
    }

    private Response validateResponseStatus(Response response, Request req, int expected) {
        if (expected != response.code()) {
            response.close();

            throw new RemoteException(name() + " responded status code " + response.code() + ", " + req.method() + " " + req.url().toString());
        }
        return response;
    }

    private int getResponseStatus(Response response) {
        try (response) {
            return response.code();
        }
    }


    @SneakyThrows
    private <T> T getEntity(Response response, Class<T> clazz) {
        try (response) {
            return gson.fromJson(response.body().charStream(), clazz);
        }
        catch (Exception ex) {
            throw ex;
        }
    }
    @SneakyThrows
    private String getText(Response response) {
        try (response) {
            return response.body().string();
        }

    }

    public boolean isAlive(int node) {
        return livenessMonitor.isAlive(node);
    }

    private String json(Object o) {
        try {
            return gson.toJson(o);
        }
        catch (Exception ex) {
            throw new LocalException(ex);
        }
    }

}
