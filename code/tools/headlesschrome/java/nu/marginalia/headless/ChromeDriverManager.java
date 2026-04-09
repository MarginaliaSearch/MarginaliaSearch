package nu.marginalia.headless;

import nu.marginalia.WmsaHome;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChromeDriverManager {
    private static final String CHROME_PATH = "/usr/bin/chromium";
    private final Path CHROME_DATA_DIR = Path.of("/tmp/chrome-data/");

    private static final Logger logger = LoggerFactory.getLogger(ChromeDriverManager.class);

    private ChromeOptions screenshotOptions;
    private ChromeOptions extensionOptions;

    private final ArrayBlockingQueue<DriverHolder> screenshotDriverHolders;
    private final ArrayBlockingQueue<DriverHolder> extensionDriverHolders;

    private AtomicInteger userDirCtr = new AtomicInteger();

    public ChromeDriverManager(int queueSize) throws IOException {
        if (Files.isDirectory(CHROME_DATA_DIR)) {
            for (Path p : Files.list(CHROME_DATA_DIR).filter(Files::isDirectory).toList()) {
                logger.info("Cleaning {}", p);
                FileUtils.deleteDirectory(p.toFile());
            }
        }
        else {
            Files.createDirectory(CHROME_DATA_DIR);
        }

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
            Path userDir = createUserDir();
            screenshotDriverHolders.add(new DriverHolder(createScreenshotDriver(userDir), screenshotDriverHolders, userDir));

            extensionDriverHolders.add(new DriverHolder(createExtensionDriver(userDir), extensionDriverHolders, userDir));
        }
    }

    private Path createUserDir() {
        return CHROME_DATA_DIR.resolve("user-"+userDirCtr.incrementAndGet());
    }

    private ChromeDriver createScreenshotDriver(Path userDir) {
        var options = new ChromeOptions().merge(screenshotOptions);

        // https://chromium.googlesource.com/chromium/src/+/HEAD/docs/user_data_dir.md#Command-Line

        options.addArguments("--user-data-dir=" + userDir);

        var driver = new ChromeDriver(options);
        driver.executeCdpCommand("Emulation.setDeviceMetricsOverride",
                Map.of("width", 1024, "height", 768,
                        "deviceScaleFactor", 1., "mobile", false));
        driver.executeCdpCommand("Emulation.setScrollbarsHidden",
                Map.of("hidden", true));
        return driver;
    }

    private ChromeDriver createExtensionDriver(Path userDir) {
        var options = new ChromeOptions().merge(extensionOptions);

        options.addArguments("--user-data-dir=" + userDir);
        return new ChromeDriver(options);
    }

    public DriverHolder getScreenshotDriver(Duration timeout) throws InterruptedException {
        var holder = screenshotDriverHolders.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (holder.isDead()) {
            try {
                holder.driver.quit();
                FileUtils.deleteDirectory(holder.userDir.toFile());
            }
            catch (Exception ex) {}
            Path userDir = createUserDir();
            holder = new DriverHolder(createScreenshotDriver(userDir), screenshotDriverHolders, userDir);
        }
        return holder;
    }



    public DriverHolder getExtensionDriver(Duration timeout) throws InterruptedException {
        var holder = extensionDriverHolders.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (holder.isDead()) {
            try {
                holder.driver.quit();
                FileUtils.deleteDirectory(holder.userDir.toFile());
            }
            catch (Exception ex) {}

            Path userDir = createUserDir();
            holder = new DriverHolder(createExtensionDriver(userDir), extensionDriverHolders, userDir);
        }
        return holder;
    }

    public void close() {
        List<DriverHolder> holderList = new ArrayList<>();

        extensionDriverHolders.drainTo(holderList);
        screenshotDriverHolders.drainTo(holderList);

        holderList.forEach(holder -> {
            holder.driver.quit();
        });
    }

    public class DriverHolder implements AutoCloseable {
        private final ChromeDriver driver;
        private final ArrayBlockingQueue<DriverHolder> queue;
        private final Path userDir;
        private AtomicInteger uses = new AtomicInteger(0);
        private AtomicBoolean dead = new AtomicBoolean(false);

        DriverHolder(ChromeDriver driver,
                     ArrayBlockingQueue<DriverHolder> queue,
                     Path userDir) {
            this.driver = driver;
            this.queue = queue;
            this.userDir = userDir;
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
