package nu.marginalia.screenshot;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.module.DatabaseModule;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;

public class ScreenshotCaptureToolMain {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotCaptureToolMain.class);

    public static void main(String[] args) {
        DatabaseModule databaseModule = new DatabaseModule();
        var ds = databaseModule.provideConnection();

        System.setProperty(ChromeDriverService.CHROME_DRIVER_SILENT_OUTPUT_PROPERTY, "true");

        ChromeDriver driver = initChromeDriver();
        List<EdgeDomain> crawlQueue = fetchCrawlQueue(ds, 1000);

        HttpClient httpClient = HttpClient.newHttpClient();

        try (Connection conn = ds.getConnection()) {


            logger.info("Probing domains");
            var ret = crawlQueue.parallelStream().collect(Collectors.partitioningBy(domain -> probeUrl(httpClient, domain)));

            var badDomains = ret.getOrDefault(Boolean.FALSE, Collections.emptyList());
            var goodDomains = ret.getOrDefault(Boolean.TRUE, Collections.emptyList());

            logger.info("Result: {} good domains, {} bad domains", goodDomains.size(), badDomains.size());

            badDomains.forEach(domain -> flagDomainAsFetched(conn, domain));

            for (var domain : goodDomains) {
                logger.info("Fetching {}", domain);

                var filePath = fetchDomain(driver, domain);
                if (filePath != null) {
                    uploadScreenshot(conn, domain, filePath);
                } else {
                    flagDomainAsFetched(conn, domain);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    @NotNull
    private static ChromeDriver initChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "./chromedriver");
        ChromeOptions options = new ChromeOptions();

        options.setPageLoadStrategy(PageLoadStrategy.NONE);
        options.setPageLoadTimeout(Duration.ofSeconds(30));

        options.addArguments(
                "no-sandbox",
                "headless",
                "user-agent=search.marginalia.nu",
                "window-size=1024,768",
                "force-device-scale-factor=0.5",
                "high-dpi-support=0.5",
                "dns-prefetch-disable",
                "disable-gpu",
                "disable-dev-shm-usage",
                "disable-software-rasterizer",
                "disable-extensions"
                );

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

    private static boolean probeUrl(HttpClient httpClient, EdgeDomain domain) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(new URI(domain.toRootUrl().toString()))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("user-agent", "search.marginialia.nu")
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() < 400;
        } catch (Exception ex) {
            return false;
        }

    }
    private static Path fetchDomain(ChromeDriver driver, EdgeDomain domain) {
        try {
            driver.get(domain.toRootUrl().toString());

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            try {
                wait.until((ExpectedCondition<Boolean>) wd -> {
                    if (wd instanceof JavascriptExecutor jse) {
                        return "complete".equals(jse.executeScript("return document.readyState"));
                    }
                    return true;
                });
            }
            catch (TimeoutException ex) {
                logger.info("Wait timed out, forcing window.stop()");
                driver.executeScript("window.stop()");
            }



            final byte[] bytes = driver.getScreenshotAs(OutputType.BYTES);

            final var img = ImageIO.read(new ByteArrayInputStream(bytes));


            Path destPath = Files.createTempFile("website-screenshot-", ".webp");
            ImageIO.write(img, "webp", destPath.toFile());

            // If the screenshot is very small by size, it's very likely not particularly interesting to look at
            if (Files.size(destPath) < 3500) {
                Files.delete(destPath);
                return null;
            }

            return destPath;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
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
