package nu.marginalia.screenshot.tool;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.module.DatabaseModule;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

public class ScreenshotCaptureToolMain {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotCaptureToolMain.class);

    public static void main(String[] args) {
        DatabaseModule databaseModule = new DatabaseModule();
        var ds = databaseModule.provideConnection();

        ChromeDriver driver = initChromeDriver(args);
        List<EdgeDomain> crawlQueue = getDomains(args, ds);

        try (Connection conn = ds.getConnection()) {
            for (var domain : crawlQueue) {
                logger.info("Fetching {}", domain);

                fetchDomain(driver, domain)
                        .ifPresentOrElse(
                                (path) -> uploadScreenshot(conn, domain, path),
                                () -> flagDomainAsFetched(conn, domain));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    @NotNull
    private static List<EdgeDomain> getDomains(String[] args, HikariDataSource ds) {
        List<EdgeDomain> crawlQueue;
        if (args.length <= 1) {
            crawlQueue = fetchCrawlQueue(ds, 100);
        }
        else {
            crawlQueue = Arrays.stream(args).skip(1).map(EdgeDomain::new).toList();
        }
        return crawlQueue;
    }

    @NotNull
    private static ChromeDriver initChromeDriver(String[] args) {
        System.setProperty("webdriver.chrome.driver", args[0]);
        ChromeOptions options = new ChromeOptions();

        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.setPageLoadTimeout(Duration.ofSeconds(30));

        options.addArguments(
                "no-sandbox",
                "headless",
                "user-agent=search.marginalia.nu",
                "window-size=1024,768",
                "force-device-scale-factor=0.5",
                "high-dpi-support=0.5",
                "disable-gpu",
                "disable-dev-shm-usage",
                "disable-software-rasterizer");

        return new ChromeDriver(options);
    }

    private static void flagDomainAsFetched(Connection conn, EdgeDomain domain) {
        try (var stmt = conn.prepareStatement("REPLACE INTO DATA_DOMAIN_HISTORY(DOMAIN_NAME, SCREENSHOT_DATE) VALUES (?, NOW())")) {
            stmt.setString(1, domain.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void uploadScreenshot(Connection conn, EdgeDomain domain, Path screenshotPath) {
        logger.info("Uploading {}", screenshotPath);
        try (var stmt = conn.prepareStatement("REPLACE INTO DATA_DOMAIN_SCREENSHOT(DOMAIN_NAME, CONTENT_TYPE, DATA) VALUES (?,?,?)");
             var is = Files.newInputStream(screenshotPath)
        ) {
            stmt.setString(1, domain.toString());
            stmt.setString(2, "image/webp");
            stmt.setBlob(3, is);
            stmt.executeUpdate();

            Files.delete(screenshotPath);

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }


        flagDomainAsFetched(conn, domain);
    }

    private static Optional<Path> fetchDomain(ChromeDriver driver, EdgeDomain domain) {
        try {
            driver.get(domain.toRootUrl().toString());

            final byte[] bytes = driver.getScreenshotAs(OutputType.BYTES);

            final var img = ImageIO.read(new ByteArrayInputStream(bytes));

            Path destPath = Files.createTempFile("website-screenshot-", ".webp");
            ImageIO.write(img, "webp", destPath.toFile());

            // If the screenshot is very small by size, it's very likely not particularly interesting to look at
            if (Files.size(destPath) < 2500) {
                Files.delete(destPath);
                return Optional.empty();
            }

            return Optional.of(destPath);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    private static List<EdgeDomain> fetchCrawlQueue(HikariDataSource ds, int queueSize) {
        List<EdgeDomain> ret = new ArrayList<>(queueSize);

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            var rsp = stmt.executeQuery(
                    """
                    SELECT EC_DOMAIN.DOMAIN_NAME FROM EC_DOMAIN
                    LEFT JOIN DATA_DOMAIN_HISTORY ON EC_DOMAIN.DOMAIN_NAME=DATA_DOMAIN_HISTORY.DOMAIN_NAME
                    ORDER BY SCREENSHOT_DATE IS NULL DESC, SCREENSHOT_DATE, INDEXED DESC
                    LIMIT 
                    """ + queueSize);
            while (rsp.next()) {
                ret.add(new EdgeDomain(rsp.getString(1)));
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
            return Collections.emptyList();
        }
        return ret;
    }
}
