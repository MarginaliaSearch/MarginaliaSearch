package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.github.luben.zstd.ZstdOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CrawlerAuditLog implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(CrawlerAuditLog.class);

    private final Path logFile;
    private final ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(256);
    private final Thread writerThread;

    private volatile boolean running = true;

    @Inject
    public CrawlerAuditLog(ProcessConfiguration processConfiguration) {
        this(Path.of(
                System.getenv().getOrDefault("WMSA_LOG_DIR", "/var/log/wmsa"),
                "crawler-audit-" + processConfiguration.node() + ".log.zstd"
        ));
    }

    public CrawlerAuditLog(Path logFile) {
        this.logFile = logFile;
        this.writerThread = Thread.ofPlatform()
                             .name("crawler-audit-log")
                             .start(this::writerThread);
    }

    private void writerThread() {
        final BufferedOutputStream writer;

        try {
            Files.createDirectories(logFile.getParent());
            writer = new BufferedOutputStream(new ZstdOutputStream(Files.newOutputStream(logFile), -1), 65536);
        } catch (IOException e) {
            running = false;
            logger.error("Error creating ZSTD output stream", e);
            return;
        }

        try (writer) {
            while (running && !Thread.interrupted()) {
                final String message = messages.poll(100, TimeUnit.MILLISECONDS);

                if (message == null)
                    continue;

                writeMessage(writer, message);
            }

            running = false;

            // Consume lingering entries in queue
            while (!messages.isEmpty()) {
                String message = messages.take();
                writeMessage(writer, message);
            }
        }
        catch (InterruptedException ex) {
            logger.warn("Interrupted while waiting for ZSTD output stream", ex);
        }
        catch (IOException ex) {
            logger.warn("Error closing crawler audit log", ex);
        }

        running = false;
    }

    private void writeMessage(BufferedOutputStream writer, String message) {
        try {
            writer.write(message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            logger.warn("Error writing crawler audit log", ex);
        }
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

        enqueue(msg);
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

        enqueue(msg);
    }

    private void enqueue(String msg) {
        // cheap path when queue isn't empty, or we're closed
        if (!running || messages.offer(msg))
            return;

        try {
            // We offer with timeout to protect against unlikely TOCTOU in running state that could deadlock the caller
            // if the writer thread isn't around anymore to drain the messages queue.
            while (running && !messages.offer(msg, 30, TimeUnit.SECONDS));
        }
        catch (InterruptedException ex) {
            logger.warn("Interrupted while waiting for audit log submission", ex);
            Thread.currentThread().interrupt();
        }
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
