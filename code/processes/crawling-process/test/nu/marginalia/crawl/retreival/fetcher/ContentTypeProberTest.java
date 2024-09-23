package nu.marginalia.crawl.retreival.fetcher;

import nu.marginalia.crawl.logic.ContentTypeProber;
import nu.marginalia.crawl.logic.ContentTypeProber.ContentTypeProbeResult.BadContentType;
import nu.marginalia.crawl.logic.ContentTypeProber.ContentTypeProbeResult.Ok;
import nu.marginalia.model.EdgeUrl;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentTypeProberTest {

    ContentTypeProber prober;

    @BeforeEach
    void setUp() {
        OkHttpClient client = new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(Executors.newVirtualThreadPerTaskExecutor()))
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .build();

        prober = new ContentTypeProber("test.marginalia.nu", client);
    }

    @Test
    void probeContentType() throws URISyntaxException {
        assertEquals(
                new Ok(new EdgeUrl("https://www.marginalia.nu/robots.txt")),
                prober.probeContentType(new EdgeUrl("https://www.marginalia.nu/robots.txt")),
                "robots.txt is expected to pass the probing test since it's text/plain"
        );

        assertEquals(
                new BadContentType("image/png", 200),
                prober.probeContentType(new EdgeUrl("https://www.marginalia.nu/sanic.png")),
                "sanic.png is expected to pass the probing test since it's image/png"
        );

        assertEquals(
                new Ok(new EdgeUrl("https://www.marginalia.nu/dev/null")),
                prober.probeContentType(new EdgeUrl("https://www.marginalia.nu/dev/null")),
                "Despite being a 404, we expect this to be passed as OK as it's NotMyJob(TM) to verify response codes"
        );

        assertEquals(
                new Ok(new EdgeUrl("https://www.marginalia.nu/projects/edge/about.gmi/")),
                prober.probeContentType(new EdgeUrl("https://www.marginalia.nu/projects/edge/about.gmi")),
                "about.gmi is expected to give a redirect to about.gmi/ which is served as text/html"
        );

    }
}