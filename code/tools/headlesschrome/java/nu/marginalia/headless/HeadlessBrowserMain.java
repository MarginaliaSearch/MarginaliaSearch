package nu.marginalia.headless;

import com.google.gson.Gson;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.ServerOptions;
import io.jooby.StatusCode;
import nu.marginalia.model.gson.GsonFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class HeadlessBrowserMain extends Jooby {
    private static final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(HeadlessBrowserMain.class);

    private ChromeDriverManager driverManager = new ChromeDriverManager(4);

    static void main(String[] args) {
        Jooby.runApp(args, HeadlessBrowserMain::new);
    }

    public HeadlessBrowserMain() {
        var options = new ServerOptions();
        options.setHost("0.0.0.0");
        options.setPort(8080);
        options.setCompressionLevel(1);

        options.setWorkerThreads(Math.min(4, options.getWorkerThreads()));
        options.setIoThreads(Math.min(4, options.getIoThreads()));

        setServerOptions(options);

        get("/health", this::health);
        post("/screenshot", this::screenshot);
        post("/dom-sample", this::domSample);
    }

    public String health(Context context) {

        context.setResponseCode(StatusCode.OK_CODE);
        context.setResponseType("application/json");

        return gson.toJson(Map.of(
                "status", "ok"
        ));
    }

    public Object screenshot(Context ctx) throws InterruptedException {

        ScreenshotRequest request = gson.fromJson(ctx.body().value(StandardCharsets.UTF_8), ScreenshotRequest.class);

        logger.info("Fetching screenshot {}", request.url);

        try (var driverHolder = driverManager.getVanillaDriver(Duration.ofSeconds(15))){
            ChromeDriver driver = driverHolder.get();

            driver.get(request.url());

            waitForNetworkIdle(driver, Duration.ofSeconds(10));

            ctx.setResponseType("image/png");
            return driver.getScreenshotAs(OutputType.BYTES);
        }

    }

    public Object domSample(Context ctx) throws InterruptedException {
        DomSampleRequest request = gson.fromJson(ctx.body().value(StandardCharsets.UTF_8), DomSampleRequest.class);

        logger.info("Fetching DOM sample {}", request.url);

        try (var holder = driverManager.getExtensionDriver(Duration.ofSeconds(15))) {
            ChromeDriver driver = holder.get();

            driver.get(request.url());

            new WebDriverWait(driver, Duration.ofSeconds(15))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.id("marginaliahack")));

            // FIXME: What if it's not?
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


}
