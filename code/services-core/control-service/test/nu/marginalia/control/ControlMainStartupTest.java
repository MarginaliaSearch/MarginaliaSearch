package nu.marginalia.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import io.jooby.Jooby;
import io.jooby.ServerOptions;
import io.jooby.netty.NettyServer;
import nu.marginalia.service.server.Initialization;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ControlMainStartupTest {
    private final Initialization initialization = new Initialization();
    private final AtomicInteger announcements = new AtomicInteger();

    private Jooby app() {
        initialization.addCallback(announcements::incrementAndGet);
        var injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ControlService.class).toInstance(mock(ControlService.class));
                bind(Initialization.class).toInstance(initialization);
            }
        });
        var app = new Jooby();
        injector.getInstance(ControlMain.class).start(app);
        app.get("/ping", ctx -> "pong");
        return app;
    }

    @Test
    void announcesReadinessAfterServerStarts() throws Exception {
        var app = app();
        assertFalse(initialization.isReady());
        assertEquals(0, announcements.get());
        var server = new NettyServer(new ServerOptions().setHost("127.0.0.1").setPort(0));
        try (var client = HttpClient.newHttpClient()) {
            server.start(app);
            assertTrue(initialization.isReady());
            assertEquals(1, announcements.get());
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + server.getOptions().getPort() + "/ping")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("pong", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void doesNotAnnounceReadinessWhenStartupFails() {
        var app = app();
        app.onStarting(() -> { throw new IllegalStateException("Startup failure"); });
        var server = new NettyServer(new ServerOptions().setHost("127.0.0.1").setPort(0));
        try {
            assertThrows(Exception.class, () -> server.start(app));
            assertFalse(initialization.isReady());
            assertEquals(0, announcements.get());
        } finally {
            server.stop();
        }
    }
}
