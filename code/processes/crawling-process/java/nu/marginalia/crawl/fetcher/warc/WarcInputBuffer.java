package nu.marginalia.crawl.fetcher.warc;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.netpreserve.jwarc.WarcTruncationReason;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static nu.marginalia.crawl.fetcher.warc.ErrorBuffer.suppressContentEncoding;

/** Input buffer for temporary storage of a HTTP response
 *  This may be in-memory or on-disk, at the discretion of
 *  the implementation.
 * */
public abstract class WarcInputBuffer implements AutoCloseable {
    protected WarcTruncationReason truncationReason = WarcTruncationReason.NOT_TRUNCATED;
    protected Header[] headers;

    WarcInputBuffer(Header[] headers) {
        this.headers = headers;
    }

    /** If necessary, the stream is closed when the buffer is closed */
    public abstract InputStream read() throws IOException;

    /** The size of the response */
    public abstract int size();

    public final WarcTruncationReason truncationReason() { return truncationReason; }

    public final Header[] headers() { return headers; }

    /** Create a buffer for a response.
     *  If the response is small and not compressed, it will be stored in memory.
     *  Otherwise, it will be stored in a temporary file, with compression transparently handled
     *  and suppressed from the headers.
     *  If an error occurs, a buffer will be created with no content and an error status.
     */
    static WarcInputBuffer forResponse(ClassicHttpResponse response, Duration timeLimit) throws IOException {
        if (response == null)
            return new ErrorBuffer();


        var entity = response.getEntity();

        if (null == entity) {
            System.out.println("Null entity on " + response.getCode() + " : " + response.getReasonPhrase());
            return new ErrorBuffer();
        }

        InputStream is = entity.getContent();
        long length = entity.getContentLength();

        try (response) {
            if (length > 0 && length < 8192) {
                // If the content is small and not compressed, we can just read it into memory
                return new MemoryBuffer(response.getHeaders(), timeLimit, is, (int) length);
            } else {
                // Otherwise, we unpack it into a file and read it from there
                return new FileBuffer(response.getHeaders(), timeLimit, is);
            }
        }


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

                int n = is.read(buffer);

                if (n < 0) break;
                size += n;
                os.write(buffer, 0, n);

                if (size > WarcRecorder.MAX_SIZE) {
                    truncationReason = WarcTruncationReason.LENGTH;
                    break;
                }
            } catch (IOException e) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            }
        }
    }

}

/** Pseudo-buffer for when we have an error */
class ErrorBuffer extends WarcInputBuffer {
    public ErrorBuffer() {
        super(new Header[0]);

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


    static Header[] suppressContentEncoding(Header[] headers) {
        return Arrays.stream(headers).filter(header -> !"Content-Encoding".equalsIgnoreCase(header.getName())).toArray(Header[]::new);
    }

}

/** Buffer for when we have the response in memory */
class MemoryBuffer extends WarcInputBuffer {
    byte[] data;
    public MemoryBuffer(Header[] headers, Duration timeLimit, InputStream responseStream, int size) {
        super(suppressContentEncoding(headers));

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

    public FileBuffer(Header[] headers, Duration timeLimit, InputStream responseStream) throws IOException {
        super(suppressContentEncoding(headers));

        this.tempFile = Files.createTempFile("rsp", ".html");

        try (var out = Files.newOutputStream(tempFile)) {
            copy(responseStream, out, timeLimit);
        }
        catch (Exception ex) {
            truncationReason = WarcTruncationReason.UNSPECIFIED;
        }
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