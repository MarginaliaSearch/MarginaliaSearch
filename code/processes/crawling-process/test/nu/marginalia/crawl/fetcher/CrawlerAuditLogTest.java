package nu.marginalia.crawl.fetcher;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CrawlerAuditLogTest {

    @Test
    void logProbe(@TempDir Path tmpDir) throws IOException, URISyntaxException {
        Path logFile = tmpDir.resolve("test.log.zstd");
        CrawlerAuditLog log = new CrawlerAuditLog(logFile);

        try {
            var url = new EdgeUrl("https://www.marginalia.nu/");
            log.logProbe(new HttpFetcher.ContentTypeProbeResult.Ok(url), url);
            log.logProbe(new HttpFetcher.ContentTypeProbeResult.Ok(url), url);
        }
        finally {
            log.close();
        }

        try (var zis = new ZstdInputStream(Files.newInputStream(logFile))) {
            System.out.println(new String(zis.readAllBytes()));
        }
    }

    @Test
    void logFetch(@TempDir Path tmpDir) throws IOException, URISyntaxException {
        Path logFile = tmpDir.resolve("test.log.zstd");
        CrawlerAuditLog log = new CrawlerAuditLog(logFile);

        try {
            var url = new EdgeUrl("https://www.marginalia.nu/");
            log.logFetch(new HttpFetchResult.ResultException(new RuntimeException()), url, Duration.ofMillis(50));
            log.logFetch(new HttpFetchResult.ResultException(new RuntimeException()), url, Duration.ofMillis(50));
        }
        finally {
            log.close();
        }

        try (var zis = new ZstdInputStream(Files.newInputStream(logFile))) {
            System.out.println(new String(zis.readAllBytes()));
        }
    }
}