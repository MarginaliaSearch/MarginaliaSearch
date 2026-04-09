package nu.marginalia.headless;

import com.google.gson.Gson;
import io.jooby.*;
import nu.marginalia.model.gson.GsonFactory;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class HeadlessBrowserMain extends Jooby {
    private static final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(HeadlessBrowserMain.class);

    private ChromeDriverManager driverManager = new ChromeDriverManager();

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

        ChromeDriver driver = driverManager.getDriver();

        driver.get(request.url());

        // FIXME: Wait for network idle type logic
        Thread.sleep(Duration.ofSeconds(10));

        ctx.setResponseType("image/png");
        return driver.getScreenshotAs(OutputType.BYTES);
    }

    public Object domSample(Context ctx) throws InterruptedException {
        DomSampleRequest request = gson.fromJson(ctx.body().value(StandardCharsets.UTF_8), DomSampleRequest.class);

        logger.info("Fetching DOM sample {}", request.url);

        ChromeDriver driver = driverManager.getDriver();

        driver.get(request.url());

        // FIXME: Add proper termination criteria
        Thread.sleep(Duration.ofSeconds(10));

        // FIXME: What if it's not?
        ctx.setResponseType("text/html; charset=utf-8");

        return driver.getPageSource().getBytes(StandardCharsets.UTF_8);
    }

    record ScreenshotRequest(String url) {}
    record DomSampleRequest(String url) {}


}
