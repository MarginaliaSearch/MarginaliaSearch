package nu.marginalia.screenshot;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.module.DatabaseModule;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ScreenshotCaptureToolMain {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotCaptureToolMain.class);

    public static void main(String[] args) {
        DatabaseModule databaseModule = new DatabaseModule(false);
        var ds = databaseModule.provideConnection();

        System.setProperty(ChromeDriverService.CHROME_DRIVER_SILENT_OUTPUT_PROPERTY, "true");

        List<EdgeDomain> crawlQueue = fetchCrawlQueue(ds, 1000);

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                ;

        try (Connection conn = ds.getConnection()) {
            for (var domain : crawlQueue) {
                logger.info("Fetching {}", domain);

                byte[] webpBytes = fetchDomain(httpClient, domain);
                if (webpBytes != null) {
                    uploadScreenshot(conn, domain, webpBytes);
                } else {
                    flagDomainAsFetched(conn, domain);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void flagDomainAsFetched(Connection conn, EdgeDomain domain) {
        try (var stmt = conn.prepareStatement("""
                REPLACE INTO DATA_DOMAIN_HISTORY(DOMAIN_NAME, SCREENSHOT_DATE) 
                VALUES (?, NOW())
                """))
        {
            stmt.setString(1, domain.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void uploadScreenshot(Connection conn, EdgeDomain domain, byte[] webpBytes) {
        try (var stmt = conn.prepareStatement("""
                REPLACE INTO DATA_DOMAIN_SCREENSHOT(DOMAIN_NAME, CONTENT_TYPE, DATA) 
                VALUES (?,?,?)
                """);
             var is = new ByteArrayInputStream(webpBytes)
        ) {
            stmt.setString(1, domain.toString());
            stmt.setString(2, "image/png");
            stmt.setBlob(3, is);
            stmt.executeUpdate();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }

        flagDomainAsFetched(conn, domain);
    }

    private static Gson gson = GsonFactory.get();

    private static byte[] fetchDomain(HttpClient client, EdgeDomain domain) {
        try {
            Map<String, Object> requestData = Map.of(
                    "url", domain.toRootUrl().toString(),
                    "options",
                    Map.of("fullPage", false,
                            "type", "png"),
                    "gotoOptions", Map.of(
                            "waitUntil", "networkidle2",
                            "timeout", TimeUnit.SECONDS.toMillis(10)
                        )
            );

            var request = HttpRequest.newBuilder()
                    .uri(new URI("http://browserless:3000/screenshot"))
                    .method("POST", HttpRequest.BodyPublishers.ofString(
                            gson.toJson(requestData)
                    ))
                    .header("Content-type", "application/json")
                    .build();
            var rsp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (rsp.statusCode() >= 300) {
                return null;
            }

            byte[] image = rsp.body();
            if (image.length < 3500) {
                logger.warn("Skipping {} due to size ({})", domain, image.length);
                return null;
            }

            return image;
        }
        catch (Exception ex) {
            logger.warn("Exception in screenshotting " + domain, ex);
            return null;
        }
    }

    private static List<EdgeDomain> fetchCrawlQueue(HikariDataSource ds, int queueSize) {

        List<EdgeDomain> ret = new ArrayList<>(queueSize);

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            int newCount = queueSize / 4;
            int oldCount = queueSize - newCount;

            ResultSet rst = stmt.executeQuery(
                    """
                    SELECT EC_DOMAIN.DOMAIN_NAME FROM EC_DOMAIN
                    LEFT JOIN DATA_DOMAIN_HISTORY ON EC_DOMAIN.DOMAIN_NAME=DATA_DOMAIN_HISTORY.DOMAIN_NAME
                    ORDER BY SCREENSHOT_DATE IS NULL DESC, SCREENSHOT_DATE, INDEXED DESC
                    LIMIT
                    """ + newCount);
            while (rst.next()) {
                ret.add(new EdgeDomain(rst.getString(1)));
            }

            rst = stmt.executeQuery("""
                SELECT DATA_DOMAIN_HISTORY.DOMAIN_NAME FROM DATA_DOMAIN_HISTORY
                INNER JOIN DATA_DOMAIN_SCREENSHOT ON DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME = DATA_DOMAIN_HISTORY.DOMAIN_NAME
                WHERE SCREENSHOT_DATE IS NOT NULL
                ORDER BY SCREENSHOT_DATE ASC
                """ + oldCount);

            while (rst.next()) {
                ret.add(new EdgeDomain(rst.getString(1)));
            }


        }
        catch (Exception ex) {
            logger.warn("Exception in fetching queue", ex);
            return Collections.emptyList();
        }
        return ret;
    }
}
