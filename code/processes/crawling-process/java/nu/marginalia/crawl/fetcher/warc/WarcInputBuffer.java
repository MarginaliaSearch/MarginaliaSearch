package nu.marginalia.crawl.fetcher.warc;

import org.apache.commons.io.input.BOMInputStream;
import org.netpreserve.jwarc.WarcTruncationReason;

import java.io.*;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Input buffer for temporary storage of a HTTP response
 *  This may be in-memory or on-disk, at the discretion of
 *  the implementation.
 * */
public abstract class WarcInputBuffer implements AutoCloseable {
    protected WarcTruncationReason truncationReason = WarcTruncationReason.NOT_TRUNCATED;
    protected HttpHeaders headers;

    WarcInputBuffer(HttpHeaders headers) {
        this.headers = headers;
    }

    /** If necessary, the stream is closed when the buffer is closed */
    public abstract InputStream read() throws IOException;

    /** The size of the response */
    public abstract int size();

    public final WarcTruncationReason truncationReason() { return truncationReason; }

    public final HttpHeaders headers() { return headers; }

    /** Create a buffer for a response.
     *  If the response is small and not compressed, it will be stored in memory.
     *  Otherwise, it will be stored in a temporary file, with compression transparently handled
     *  and suppressed from the headers.
     *  If an error occurs, a buffer will be created with no content and an error status.
     */
    static WarcInputBuffer forResponse(HttpResponse<InputStream> rsp, Duration timeLimit) {
        if (rsp == null)
            return new ErrorBuffer();

        var headers = rsp.headers();

        try (var is = rsp.body()) {
            int contentLength = (int) headers.firstValueAsLong("Content-Length").orElse(-1L);
            String contentEncoding = headers.firstValue("Content-Encoding").orElse(null);

            if (contentEncoding == null && contentLength > 0 && contentLength < 8192) {
                // If the content is small and not compressed, we can just read it into memory
                return new MemoryBuffer(headers, timeLimit, is, contentLength);
            }
            else {
                // Otherwise, we unpack it into a file and read it from there
                return new FileBuffer(headers, timeLimit, is);
            }
        }
        catch (Exception ex) {
            return new ErrorBuffer();
        }

    }

    private static final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();

    private Future<Integer> readAsync(InputStream is, byte[] out) {
        return virtualExecutorService.submit(() -> is.read(out));
    }

    /** Copy an input stream to an output stream, with a maximum size and time limit */
    protected void copy(InputStream is, OutputStream os, Duration timeLimit) {
        Instant start = Instant.now();
        Instant timeout = start.plus(timeLimit);
        long size = 0;

        byte[] buffer = new byte[8192];

        // Gobble up the BOM if it's there
        is = new BOMInputStream(is);

        while (true) {
            try {
                Duration remaining = Duration.between(Instant.now(), timeout);
                if (remaining.isNegative()) {
                    truncationReason = WarcTruncationReason.TIME;
                    break;
                }

                Future<Integer> readAsync = readAsync(is, buffer);
                int n = readAsync.get(remaining.toMillis(), TimeUnit.MILLISECONDS);

                if (n < 0) break;
                size += n;
                os.write(buffer, 0, n);

                if (size > WarcRecorder.MAX_SIZE) {
                    truncationReason = WarcTruncationReason.LENGTH;
                    break;
                }
            } catch (IOException|ExecutionException e) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            } catch (TimeoutException e) {
                truncationReason = WarcTruncationReason.TIME;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}

/** Pseudo-buffer for when we have an error */
class ErrorBuffer extends WarcInputBuffer {
    public ErrorBuffer() {
        super(HttpHeaders.of(Map.of(), (k,v)->false));

        truncationReason = WarcTruncationReason.UNSPECIFIED;
    }

    @Override
    public InputStream read() throws IOException {
        return ByteArrayInputStream.nullInputStream();
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void close() throws Exception {}
}

/** Buffer for when we have the response in memory */
class MemoryBuffer extends WarcInputBuffer {
    byte[] data;
    public MemoryBuffer(HttpHeaders headers, Duration timeLimit, InputStream responseStream, int size) {
        super(headers);

        var outputStream = new ByteArrayOutputStream(size);

        copy(responseStream, outputStream, timeLimit);

        data = outputStream.toByteArray();
    }
    @Override
    public InputStream read() throws IOException {
        return new ByteArrayInputStream(data);
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public void close() throws Exception {

    }
}

/** Buffer for when we have the response in a file */
class FileBuffer extends WarcInputBuffer {
    private final Path tempFile;

    public FileBuffer(HttpHeaders headers, Duration timeLimit, InputStream responseStream) throws IOException {
        super(suppressContentEncoding(headers));

        this.tempFile = Files.createTempFile("rsp", ".html");


        if ("gzip".equalsIgnoreCase(headers.firstValue("Content-Encoding").orElse(""))) {
            try (var out = Files.newOutputStream(tempFile)) {
                copy(new GZIPInputStream(responseStream), out, timeLimit);
            }
            catch (Exception ex) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            }
        }
        else {
            try (var out = Files.newOutputStream(tempFile)) {
                copy(responseStream, out, timeLimit);
            }
            catch (Exception ex) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            }
        }
    }

    private static HttpHeaders suppressContentEncoding(HttpHeaders headers) {
        return HttpHeaders.of(headers.map(), (k, v) -> {
            if ("Content-Encoding".equalsIgnoreCase(k)) {
                return false;
            }
            return !"Transfer-Encoding".equalsIgnoreCase(k);
        });
    }


    public InputStream read() throws IOException {
        return Files.newInputStream(tempFile);
    }

    public int size() {
        try {
            long fileSize = Files.size(tempFile);
            if (fileSize > Integer.MAX_VALUE) {
                throw new IllegalStateException("File too large");
            }
            return (int) fileSize;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        Files.deleteIfExists(tempFile);
    }
}