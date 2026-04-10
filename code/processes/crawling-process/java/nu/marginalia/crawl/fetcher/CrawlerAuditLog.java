package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.crawl.fetcher.HttpFetcher.ContentTypeProbeResult;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.process.ProcessConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.github.luben.zstd.ZstdOutputStream;

@Singleton
public class CrawlerAuditLog implements Closeable {

    private final PrintWriter writer;

    @Inject
    public CrawlerAuditLog(ProcessConfiguration processConfiguration) {
        this(Path.of(
                System.getenv().getOrDefault("WMSA_LOG_DIR", "/var/log/wmsa"),
                "crawler-audit-" + processConfiguration.node() + ".log.zstd"
        ));
    }

    public CrawlerAuditLog(Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            this.writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(new ZstdOutputStream(Files.newOutputStream(logFile)))
            ));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void logProbe(ContentTypeProbeResult result, EdgeUrl url) {
        synchronized (this) {
            switch (result) {
                case ContentTypeProbeResult.NoOp() -> {}
                case ContentTypeProbeResult.Ok(EdgeUrl resolvedUrl) ->
                    writer.printf("%s: Probe OK %s\n", timestamp(), url);
                case ContentTypeProbeResult.BadContentType(String contentType, int statusCode) ->
                    writer.printf("%s: Probe BadContentType (%s) %s\n", timestamp(), contentType, url);
                case ContentTypeProbeResult.Timeout(Exception ex) ->
                    writer.printf("%s: Probe Timeout %s\n", timestamp(), url);
                case ContentTypeProbeResult.Exception(Exception ex) ->
                    writer.printf("%s: Probe Exception (%s) %s\n", timestamp(), ex.getClass().getSimpleName(), url);
                case ContentTypeProbeResult.HttpError(int statusCode, String message) ->
                    writer.printf("%s: Probe HTTP %d %s\n", timestamp(), statusCode, url);
                case ContentTypeProbeResult.Redirect(EdgeUrl location) ->
                    writer.printf("%s: Probe Redirect %s -> %s\n", timestamp(), url, location);
            }
        }
    }

    public void logFetch(HttpFetchResult result, EdgeUrl url, Duration fetchDuration) {
        synchronized (this) {
            switch (result) {
                case HttpFetchResult.ResultOk ok ->
                    writer.printf("%s: Fetch OK %d %s (%d ms)\n", timestamp(), ok.statusCode(), url, fetchDuration.toMillis());
                case HttpFetchResult.ResultRedirect(EdgeUrl redirectUrl) ->
                    writer.printf("%s: Fetch Redirect %s %s\n", timestamp(), redirectUrl, url);
                case HttpFetchResult.ResultNone() ->
                    writer.printf("%s: Fetch None %s\n", timestamp(), url);
                case HttpFetchResult.ResultException(Exception ex) ->
                    writer.printf("%s: Fetch %s %s\n", ex.getClass().getSimpleName(), timestamp(), url);
                case HttpFetchResult.Result304Raw() ->
                    writer.printf("%s: Fetch 304 Raw %s\n", timestamp(), url);
                case HttpFetchResult.Result304ReplacedWithReference(var refUrl, var ct, var body) ->
                    writer.printf("%s: Fetch 304 Ref %s\n", timestamp(), url);
            }
        }
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
