package nu.marginalia.client;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import nu.marginalia.client.route.ServiceRoute;
import org.junit.jupiter.api.*;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractClientTest {

    static TestServer testServer;
    static AbstractClient client;
    Gson gson = new Gson();

    @Data @AllArgsConstructor
    private static class DummyObject {
        public int num;
        public String str;
    }

    @BeforeAll
    public static void setUp() {
        int port = new Random().nextInt(6000, 10000);
        testServer = new TestServer(port);

        client = new AbstractClient(n -> new ServiceRoute("localhost", port), 1, Gson::new) {
            @Override
            public AbortingScheduler scheduler() {
                return new AbortingScheduler(name());
            }

            @Override
            public String name() {
                return "test";
            }
        };
        client.setTimeout(1);
    }


    @AfterAll
    public static void tearDown() {
        testServer.close();
        client.close();
    }

    private void assertError(Observable<?> observable) {
        try {
            observable.blockingSubscribe();
        }
        catch (RuntimeException ex) {
            System.out.println("Got exception " + ex.getClass().getSimpleName() + " -- as expected!" );
            return;
        }
        Assertions.fail("Expected exception");
    }
    @SneakyThrows
    private Object timeout(Request request, Response response) {
        Thread.sleep(5000);
        return "yawn";
    }
    @SneakyThrows
    private Object error404(Request request, Response response) {
        Spark.halt(404);
        return "";
    }

    @Test
    public void testGetTimeout() {
        testServer.get(this::timeout);

        assertError(client.get(Context.internal(), 0, "/get"));
    }

    @Test
    public void testPostTimeout() {
        testServer.post(this::timeout);

        assertError(client.post(Context.internal(), 0, "/post", "test"));
    }

    @Test
    public void testDeleteTimeout() {
        testServer.delete(this::timeout);

        assertError(client.delete(Context.internal(), 0,"/post"));
    }

    @Test
    public void testPost404() {
        testServer.post(this::error404);

        assertError(client.post(Context.internal(), 0,"/post", "test"));
    }
    @Test
    public void testGet404() {
        testServer.get(this::error404);

        assertError(client.get(Context.internal(), 0,"/get"));
    }
    @Test
    public void testDelete404() {
        testServer.delete(this::error404);

        assertError(client.delete(Context.internal(),0, "/delete"));
    }

    @Test
    public void testGet() {
        testServer.get((req, rsp) -> "Hello World");

        assertEquals("Hello World", client.get(Context.internal(), 0,"/get").blockingFirst());
    }

    @Test
    public void testAcceptingUp() {
        testServer.setReady(true);
        assertTrue(client.isAccepting());
    }

    @Test
    public void testAcceptingDown() {
        testServer.setReady(false);
        assertFalse(client.isAccepting());
    }

    @Test
    public void testGetJson() {
        testServer.get((req, rsp) -> new DummyObject(5, "23"), new Gson()::toJson);

        assertEquals(client.get(Context.internal(), 0,"/get", DummyObject.class).blockingFirst(),
                new DummyObject(5, "23"));
    }


    @Test
    public void testDelete() {
        testServer.delete((req, rsp) -> "Hello World");

        assertTrue(client.delete(Context.internal(), 0,"/delete").blockingFirst().isGood());
    }


    @Test
    public void testPost() {
        List<DummyObject> inbox = new ArrayList<>();
        testServer.post((req, rsp) -> {
            inbox.add(gson.fromJson(req.body(), DummyObject.class));
            return "ok";
        });

        client.post(Context.internal(),0, "/post", new DummyObject(5, "23")).blockingSubscribe();
        assertEquals(1, inbox.size());
        assertEquals(new DummyObject(5, "23"), inbox.get(0));
    }

    @Test
    public void testPostGet() {
        List<DummyObject> inbox = new ArrayList<>();
        testServer.post((req, rsp) -> {
            inbox.add(gson.fromJson(req.body(), DummyObject.class));
            return new DummyObject(1, "ret");
        }, gson::toJson);

        var ret = client.postGet(Context.internal(), 0,"/post", new DummyObject(5, "23"), DummyObject.class).blockingFirst();
        assertEquals(1, inbox.size());
        assertEquals(new DummyObject(5, "23"), inbox.get(0));
        assertEquals(new DummyObject(1, "ret"), ret);
    }
}
