package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.buffering.RingBuffer;
import nu.marginalia.crawl.fetcher.HttpFetcher.ContentTypeProbeResult;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.process.ProcessConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.io.IOUtils;

@Singleton
public class CrawlerAuditLog implements Closeable {
    private final BufferedOutputStream writer;
    private final RingBuffer<String> messages = new RingBuffer<>(16);
    private volatile boolean running = true;

    private final Thread writerThread;

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
            this.writer = new BufferedOutputStream(new ZstdOutputStream(Files.newOutputStream(logFile)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        writerThread = Thread.ofPlatform().start(() -> {
            while (true) {
                String message = messages.tryTake1C();
                if (message != null) {
                    try {
                        writer.write(message.getBytes(StandardCharsets.UTF_8));
                    }
                    catch (IOException ex) {}
                } else if (!running) {
                    break;
                }
            }

            IOUtils.closeQuietly(writer);
        });
    }

    public void logProbe(ContentTypeProbeResult result, EdgeUrl url) {
        String msg = switch (result) {
                case ContentTypeProbeResult.NoOp() -> "";
                case ContentTypeProbeResult.Ok(EdgeUrl resolvedUrl) ->
                    String.format("%s: Probe OK %s\n", timestamp(), url);
                case ContentTypeProbeResult.BadContentType(String contentType, int statusCode) ->
                    String.format("%s: Probe BadContentType (%s) %s\n", timestamp(), contentType, url);
                case ContentTypeProbeResult.Timeout(Exception ex) ->
                    String.format("%s: Probe Timeout %s\n", timestamp(), url);
                case ContentTypeProbeResult.Exception(Exception ex) ->
                    String.format("%s: Probe Exception (%s) %s\n", timestamp(), ex.getClass().getSimpleName(), url);
                case ContentTypeProbeResult.HttpError(int statusCode, String message) ->
                    String.format("%s: Probe HTTP %d %s\n", timestamp(), statusCode, url);
                case ContentTypeProbeResult.Redirect(EdgeUrl location) ->
                    String.format("%s: Probe Redirect %s -> %s\n", timestamp(), url, location);
            };

        if (msg.isBlank())
            return;

        while (!messages.putNP(msg))
            Thread.yield();
    }

    public void logFetch(HttpFetchResult result, EdgeUrl url, Duration fetchDuration) {
        String msg = switch (result) {
                case HttpFetchResult.ResultOk ok ->
                        String.format("%s: Fetch OK %d %s (%d ms)\n", timestamp(), ok.statusCode(), url, fetchDuration.toMillis());
                case HttpFetchResult.ResultRedirect(EdgeUrl redirectUrl) ->
                        String.format("%s: Fetch Redirect %s %s\n", timestamp(), redirectUrl, url);
                case HttpFetchResult.ResultNone() -> String.format("%s: Fetch None %s\n", timestamp(), url);
                case HttpFetchResult.ResultException(Exception ex) ->
                        String.format("%s: Fetch %s %s\n", ex.getClass().getSimpleName(), timestamp(), url);
                case HttpFetchResult.Result304Raw() -> String.format("%s: Fetch 304 Raw %s\n", timestamp(), url);
                case HttpFetchResult.Result304ReplacedWithReference(var refUrl, var ct, var body) ->
                        String.format("%s: Fetch 304 Ref %s\n", timestamp(), url);
        };

        while (!messages.putNP(msg))
            Thread.yield();
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    public void close() throws IOException {
        running = false;
        try {
            writerThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
