package nu.marginalia.wmsa.client;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessageV3;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.exception.LocalException;
import nu.marginalia.wmsa.client.exception.NetworkException;
import nu.marginalia.wmsa.client.exception.RemoteException;
import nu.marginalia.wmsa.client.exception.RouteNotConfiguredException;
import nu.marginalia.wmsa.configuration.server.Context;
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
import java.util.zip.GZIPOutputStream;

public abstract class AbstractClient implements AutoCloseable {
    public static final String CONTEXT_OUTBOUND_REQUEST = "outbound-request";

    private final Gson gson = GsonFactory.get();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OkHttpClient client;

    private boolean quiet;
    private String url;

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    private int timeout;
    private volatile boolean alive;

    private final Thread livenessMonitor;

    public AbstractClient(String host, int port, int timeout) {
        logger.info("Creating client for {}[{}:{}]", getClass().getSimpleName(), host, port);

        this.timeout = timeout;
        client = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(6000, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();
        url = new HttpHost(host, port).toURI();

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
        url = new HttpHost(hostname, port).toURI();
    }

    @SneakyThrows
    private void monitorLiveness() {
        Thread.sleep(100); // Wait for initialization
        for (;;) {
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
        var req = ctx.paint(new Request.Builder()).url(url + "/internal/ping").get().build();

        var call = client.newCall(req);

        return Observable.just(call)
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

        var req = ctx.paint(new Request.Builder()).url(url + "/internal/ready").get().build();

        var call = client.newCall(req);

        return Observable.just(call)
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

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json(data));

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).post(body).build();
        var call = client.newCall(req);

        return Observable
                .just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
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

    @SneakyThrows
    protected synchronized Observable<HttpStatusCode> post(Context ctx, String endpoint, GeneratedMessageV3 data) {

        ensureAlive();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/protobuf"),
                data.toByteArray());

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).post(body).build();
        var call = client.newCall(req);

        logInbound(call);
        ThreadContext.put("outbound-request", url + endpoint);
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

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                json(data));

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).post(body).build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
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

        var body = RequestBody.create(mediaType, data);

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).post(body).build();
        var call = client.newCall(req);


        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put(CONTEXT_OUTBOUND_REQUEST, url + endpoint);
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

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).get().build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
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
    protected synchronized <T> Observable<List<T>> getList(Context ctx, String endpoint, Class<T> type) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).get().build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(rsp -> validateResponseStatus(rsp, req, 200))
                .map(rsp -> Arrays.asList((T[])getEntity(rsp, type.arrayType())))
                .retryWhen(this::retryHandler)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }


    protected synchronized Observable<byte[]> getBinary(Context ctx, String endpoint) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).get().build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
                .subscribeOn(scheduler().get())
                .map(this::logInbound)
                .map(Call::execute)
                .map(this::logOutbound)
                .map(rsp -> validateResponseStatus(rsp, req, 200))
                .map(this::getBinaryEntity)
                .retryWhen(this::retryHandler)
                .timeout(timeout, TimeUnit.SECONDS)
                .doFinally(() -> ThreadContext.remove("outbound-request"));
    }

    protected synchronized Observable<String> get(Context ctx, String endpoint) {
        ensureAlive();

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).get().build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
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

        var req = ctx.paint(new Request.Builder()).url(url + endpoint).delete().build();
        var call = client.newCall(req);

        return Observable.just(call)
                .map((c) -> {
                    ThreadContext.put("outbound-request", url + endpoint);
                    return c;
                })
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
                throw new RouteNotConfiguredException("Route not configured for " + name());
            }
        }
    }


    @SneakyThrows
    public void waitReady() {
        boolean accepting = isAccepting();
        if (accepting) {
            return;
        }

        logger.info("Waiting for " + name());
        do {
            Thread.sleep(1000);
        } while (!isAccepting());
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

    @SneakyThrows
    private byte[] getBinaryEntity(Response response) {
        try (response) {
            return response.body().bytes();
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

    private byte[] compressedJson(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        try {
            gson.toJson(o, new OutputStreamWriter(gos));
            gos.finish();
            return baos.toByteArray();
        }
        catch (Exception ex) {
            throw new LocalException(ex);
        }
    }

}
