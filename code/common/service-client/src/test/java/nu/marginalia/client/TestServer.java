package nu.marginalia.client;

import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.function.BiFunction;
import java.util.function.Function;

public class TestServer {
    BiFunction<Request, Response, Object> onGet;
    BiFunction<Request, Response, Object> onPost;
    BiFunction<Request, Response, Object> onDelete;


    boolean isReady;

    public TestServer(int port) {
        Spark.port(port);
        Spark.get("/internal/ping", (r,q) -> "pong");
        Spark.get("/internal/ready", this::ready);
        Spark.get("/get", (request, response) -> onGet.apply(request, response));
        Spark.post("/post", (request, response) -> onPost.apply(request, response));
        Spark.delete("/delete", (request, response) -> onDelete.apply(request, response));
    }

    private Object ready(Request request, Response response) {
        if (isReady) {
            return "";
        }
        else {
            response.status(401);
            return "bad";
        }
    }

    public void close() {
        Spark.stop();
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }

    public TestServer get(BiFunction<Request, Response, Object> onGet) { this.onGet = onGet; return this; }
    public TestServer get(BiFunction<Request, Response, Object> onGet, Function<Object, Object> transform) {
        this.onGet = onGet.andThen(transform);
        return this;
    }
    public TestServer delete(BiFunction<Request, Response, Object> onDelete) { this.onDelete = onDelete; return this; }
    public TestServer post(BiFunction<Request, Response, Object> onPost) { this.onPost = onPost; return this; }
    public TestServer post(BiFunction<Request, Response, Object> onPost, Function<Object, Object> transform) {
        this.onPost = onPost.andThen(transform); return this;
    }
}
