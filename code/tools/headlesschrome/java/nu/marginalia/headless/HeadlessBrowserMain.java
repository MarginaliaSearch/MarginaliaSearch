package nu.marginalia.headless;

import com.google.gson.Gson;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Jooby;
import io.jooby.ServerOptions;
import io.jooby.SessionStore;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import io.jooby.netty.NettyServer;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.gson.GsonFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class HeadlessBrowserMain extends Jooby {
    private static final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(HeadlessBrowserMain.class);

    private ChromeDriverManager driverManager;

    private static final String TOKEN = Objects.requireNonNullElseGet(
            System.getenv("TOKEN"),
            () -> UUID.randomUUID().toString()
    );

    private static boolean SOFT_KILL = System.getenv("SOFT_KILL") != null;
    private static boolean ALLOW_LOCAL_REQUESTS = System.getenv("ALLOW_LOCAL_REQUESTS") != null;

    private volatile boolean killRequested = false;

    static void main(String[] args) {
        var options = new ServerOptions();
        options.setCompressionLevel(1);
        options.setWorkerThreads(Math.min(4, options.getWorkerThreads()));
        options.setIoThreads(Math.min(4, options.getIoThreads()));

        Jooby.runApp(new String[] { "application.env=prod" }, new NettyServer(options), HeadlessBrowserMain::new);
    }

    public HeadlessBrowserMain() {
        try {
            driverManager = new ChromeDriverManager(6);
        }
        catch (IOException ex) {
            logger.error("Failed to initialize ChromeDriverManager", ex);
            throw new RuntimeException(ex);
        }

        setSessionStore(SessionStore.memory(Cookie.session("marginalia-session")));

        get("/health", this::health);
        post("/kill", this::kill);
        post("/screenshot", this::screenshot);
        post("/dom-sample", this::domSample);

        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            driverManager.close();
        }));
    }

    private Object kill(Context ctx) {

        if (!SOFT_KILL) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                System.exit(255);
            });
        }

        if (!TOKEN.equals(ctx.header("Authorization").valueOrNull())) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED_CODE);
            return "";
        }

        logger.info("Termination requested");

        killRequested = true;
        return "x_x";
    }

    public String health(Context ctx) {

        if (killRequested) {
            ctx.setResponseCode(StatusCode.SERVICE_UNAVAILABLE_CODE);
            return "Awaiting termination";
        }
        else {
            ctx.setResponseCode(StatusCode.OK_CODE);
            ctx.setResponseType("application/json");

            return gson.toJson(Map.of(
                    "status", "ok"
            ));
        }
    }

    public Object screenshot(Context ctx) throws InterruptedException {
        if (killRequested) {
            ctx.setResponseCode(StatusCode.SERVICE_UNAVAILABLE_CODE);
            return "";
        }

        if (!TOKEN.equals(ctx.header("Authorization").valueOrNull())) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED_CODE);
            return "";
        }

        ScreenshotRequest request = gson.fromJson(ctx.body().value(StandardCharsets.UTF_8), ScreenshotRequest.class);

        validateUrl(request.url);

        logger.info("Fetching screenshot {}", request.url);

        try (var driverHolder = driverManager.getScreenshotDriver(Duration.ofSeconds(15))) {
            ChromeDriver driver = driverHolder.get();

            driver.get(request.url());

            waitForNetworkIdle(driver, Duration.ofSeconds(10));

            ctx.setResponseType("image/png");
            return driver.getScreenshotAs(OutputType.BYTES);
        }

    }

    public Object domSample(Context ctx) throws InterruptedException {
        if (killRequested) {
            ctx.setResponseCode(StatusCode.SERVICE_UNAVAILABLE_CODE);
            return "";
        }

        if (!TOKEN.equals(ctx.header("Authorization").valueOrNull())) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED_CODE);
            return "";
        }

        DomSampleRequest request = gson.fromJson(ctx.body().value(StandardCharsets.UTF_8), DomSampleRequest.class);

        validateUrl(request.url);

        logger.info("Fetching DOM sample {}", request.url);

        try (var holder = driverManager.getExtensionDriver(Duration.ofSeconds(30))) {
            ChromeDriver driver = holder.get();

            driver.get(request.url());

            new WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.id("marginaliahack")));

            ctx.setResponseType("text/html; charset=utf-8");

            return driver.getPageSource().getBytes(StandardCharsets.UTF_8);
        }
    }

    private void waitForNetworkIdle(ChromeDriver driver, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        Instant lastChangeTime = Instant.now();

        Duration pollInterval = Duration.ofMillis(100);
        Duration idleInterval = Duration.ofMillis(500);

        int lastCount = -1;

        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(pollInterval);

            int count;
            try {
                if (driver.executeScript("return performance.getEntriesByType('resource').length") instanceof Number n) {
                    count = n.intValue();
                } else {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }

            if (count != lastCount) {
                lastCount = count;
                lastChangeTime = Instant.now();
            } else if (Instant.now().isAfter(lastChangeTime.plus(idleInterval))) {
                return;
            }
        }
    }

    record ScreenshotRequest(String url) {}
    record DomSampleRequest(String url) {}

    private static void validateUrl(String url) throws StatusCodeException {
        var parsedUrl = EdgeUrl.parse(url).orElseThrow(() -> new StatusCodeException(StatusCode.BAD_REQUEST));

        if (!("http".equalsIgnoreCase(parsedUrl.proto) || "https".equalsIgnoreCase(parsedUrl.proto)))
            throw new StatusCodeException(StatusCode.BAD_REQUEST, "Illegal schema in URL");

        if (!ALLOW_LOCAL_REQUESTS && parsedUrl.port != null)
            throw new StatusCodeException(StatusCode.BAD_REQUEST, "Port not permitted in URL");

        try {
            InetAddress address = InetAddress.getByName(parsedUrl.domain.toString());

            if (address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress())
            {
                if (!ALLOW_LOCAL_REQUESTS) {
                    throw new StatusCodeException(StatusCode.BAD_REQUEST, "URL resolves to local address");
                }
            }
        }
        catch (UnknownHostException ex) {
            throw new StatusCodeException(StatusCode.NOT_FOUND, "No such host");
        }
    }

}
