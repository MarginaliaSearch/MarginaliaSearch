package nu.marginalia.headless;

import nu.marginalia.WmsaHome;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChromeDriverManager {
    private static final String CHROME_PATH = "/usr/bin/chromium";
    private static final Logger logger = LoggerFactory.getLogger(ChromeDriverManager.class);

    private ChromeOptions screenshotOptions;
    private ChromeOptions extensionOptions;

    private final ArrayBlockingQueue<DriverHolder> screenshotDriverHolders;
    private final ArrayBlockingQueue<DriverHolder> extensionDriverHolders;

    public ChromeDriverManager(int queueSize) {
        screenshotDriverHolders = new ArrayBlockingQueue<>(queueSize);
        extensionDriverHolders = new ArrayBlockingQueue<>(queueSize);

        var baseOptions = new ChromeOptions();
        baseOptions.setBinary(CHROME_PATH);
        baseOptions.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--user-agent=" + WmsaHome.getUserAgent().uaString()
        );

        screenshotOptions = new ChromeOptions().merge(baseOptions).addArguments("--window-size=1024,768");
        extensionOptions = new ChromeOptions().merge(baseOptions).addArguments("--load-extension=/dom-export");

        for (int i = 0; i < 4; i++) {
            screenshotDriverHolders.add(new DriverHolder(createScreenshotDriver(), screenshotDriverHolders));
            extensionDriverHolders.add(new DriverHolder(createExtensionDriver(), extensionDriverHolders));
        }
    }

    private ChromeDriver createScreenshotDriver() {
        var driver = new ChromeDriver(screenshotOptions);
        driver.executeCdpCommand("Emulation.setDeviceMetricsOverride",
                Map.of("width", 1024, "height", 768,
                        "deviceScaleFactor", 1., "mobile", false));
        driver.executeCdpCommand("Emulation.setScrollbarsHidden",
                Map.of("hidden", true));
        return driver;
    }

    private ChromeDriver createExtensionDriver() {
        return new ChromeDriver(extensionOptions);
    }

    public DriverHolder getScreenshotDriver(Duration timeout) throws InterruptedException {
        var holder = screenshotDriverHolders.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (holder.isDead()) {
            try {
                holder.driver.quit();
            }
            catch (Exception ex) {}
            holder = new DriverHolder(createScreenshotDriver(), screenshotDriverHolders);
        }
        return holder;
    }



    public DriverHolder getExtensionDriver(Duration timeout) throws InterruptedException {
        var holder = extensionDriverHolders.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (holder.isDead()) {
            try {
                holder.driver.quit();
            }
            catch (Exception ex) {}
            holder = new DriverHolder(createExtensionDriver(), extensionDriverHolders);
        }
        return holder;
    }

    public class DriverHolder implements AutoCloseable {
        private final ChromeDriver driver;
        private final ArrayBlockingQueue<DriverHolder> queue;
        private AtomicInteger uses = new AtomicInteger(0);
        private AtomicBoolean dead = new AtomicBoolean(false);

        DriverHolder(ChromeDriver driver, ArrayBlockingQueue<DriverHolder> queue) {
            this.driver = driver;
            this.queue = queue;
        }

        public ChromeDriver get() {
           return driver;
        }

        public int uses() {
            return uses.get();
        }

        public boolean isDead() {
            return dead.get() || uses.get() >= 10;
        }

        public void close() {
            uses.incrementAndGet();

            try {
                driver.navigate().to("about:blank");
                driver.manage().deleteAllCookies();
                queue.add(this);
            }
            catch (Exception ex) {
                logger.info("Stuck driver, killing");
                dead.set(true);
            }
        }
    }

}
