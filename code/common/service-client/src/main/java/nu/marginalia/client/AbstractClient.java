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
import okhttp3.*;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public abstract class AbstractClient implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String CONTEXT_OUTBOUND_REQUEST = "outbound-request";
    private final Gson gson;
    private final OkHttpClient client;

    private boolean quiet;
    private String serviceRoute;
    private int timeout;


    private volatile boolean alive;
    private final Thread livenessMonitor;

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public AbstractClient(String host, int port, int timeout, Supplier<Gson> gsonProvider) {
        logger.info("Creating client for {}[{}:{}]", getClass().getSimpleName(), host, port);

        this.gson = gsonProvider.get();

        this.timeout = timeout;
        client = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(6000, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();
        serviceRoute = new HttpHost(host, port).toURI();

        RxJavaPlugins.setErrorHandler(e -> {
            if (e.getMessage() == null) {
                logger.error("Error", e);
            }
            else {
                logger.error("Error {}: {}", e.getClass().getSimpleName(), e.getMessage());
            }
        });
        livenessMonitor = new Thread(this::monitorLiveness, host + "-monitor");
        livenessMonitor.setDaemon(true);
        livenessMonitor.start();

        logger.info("Finished creating client for {}", getClass().getSimpleName());
    }

    public void setServiceRoute(String hostname, int port) {
        scheduler().abort();
        serviceRoute = new HttpHost(hostname, port).toURI();
    }

    protected String getServiceRoute() {
        return serviceRoute;
    }

    @SneakyThrows
    private void monitorLiveness() {
        Thread.sleep(100); // Wait for initialization
        try {
            for (; ; ) {
                try {
                    alive = isResponsive();
                }
                //
                catch (Exception ex) {
                    logger.warn("Oops", ex);
                }
                synchronized (livenessMonitor) {
                    if (alive) {
                        livenessMonitor.wait(1000);
                    }
                }
                if (!alive) {
                    Thread.sleep(100);
                }
            }
        }
        catch (InterruptedException ex) {
            // nothing to see here
        }
    }

    @Override
    public void close() {
        livenessMonitor.interrupt();
        scheduler().close();
    }

    public abstract AbortingScheduler scheduler();

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public abstract String name();

    public synchronized boolean isResponsive() {
        Context ctx = Context.internal("ping");
        var req = ctx.paint(new Request.Builder()).url(serviceRoute + "/internal/ping").get().build();

        return Observable.just(client.newCall(req))
                .subscribeOn(scheduler().get())
                .map(Call::execute)
                .map(this::getResponseStatus)
                .flatMap(line -> validateStatus(line, req).timeout(5000, TimeUnit.SECONDS).onErrorReturn(e -> 500))
                .onErrorReturn(error -> 500)
                .map(HttpStatusCode::new)
                .map(HttpStatusCode::isGood)
                .blockingFirst();
    }

    public synchronized boolean isAccepting() {
        Context ctx = Context.internal("ready");

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + "/internal/ready").get().build();

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
    protected synchronized Observable<HttpStatusCode> post(Context ctx, String endpoint, Object data) {

        ensureAlive();

        RequestBody body = RequestBody.create(json(data), MediaType.parse("application/json; charset=utf-8"));

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).post(body).build();

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
    protected synchronized Observable<HttpStatusCode> post(Context ctx, String endpoint, GeneratedMessageV3 data) {

        ensureAlive();

        RequestBody body = RequestBody.create(data.toByteArray(), MediaType.parse("application/protobuf"));

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).post(body).build();
        var call = client.newCall(req);

        logInbound(call);
        ThreadContext.put("outbound-request", serviceRoute + endpoint);
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
    protected synchronized <T> Observable<T> postGet(Context ctx, String endpoint, Object data, Class<T> returnType) {

        ensureAlive();

        RequestBody body = RequestBody.create(json(data), MediaType.parse("application/json"));
        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).post(body).build();

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

    protected synchronized Observable<HttpStatusCode> post(Context ctx, String endpoint, String data, MediaType mediaType) {
        ensureAlive();

        var body = RequestBody.create(data, mediaType);

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).post(body).build();
        var call = client.newCall(req);


        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put(CONTEXT_OUTBOUND_REQUEST, serviceRoute + endpoint);
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

    protected synchronized <T> Observable<T> get(Context ctx, String endpoint, Class<T> type) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).get().build();

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
    protected synchronized Observable<String> get(Context ctx, String endpoint) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).get().build();

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

    protected synchronized Observable<HttpStatusCode> delete(Context ctx, String endpoint) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(serviceRoute + endpoint).delete().build();

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
    private void ensureAlive() {
        if (!isAlive()) {
            wait(2000);
            if (!isAlive()) {
                throw new RouteNotConfiguredException("Route not configured for " + name() + " -- tried " + serviceRoute);
            }
        }
    }


    private ObservableSource<?> retryHandler(Observable<Throwable> error) {
        return error.flatMap(this::filterRetryableExceptions);
    }

    private Observable<Throwable> filterRetryableExceptions(Throwable error) throws Throwable {

        synchronized (livenessMonitor) {
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

    public boolean isAlive() {
        return alive;
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
