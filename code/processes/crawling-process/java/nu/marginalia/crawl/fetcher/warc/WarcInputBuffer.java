package nu.marginalia.crawl.fetcher.warc;

import okhttp3.Headers;
import okhttp3.Response;
import org.apache.commons.io.input.BOMInputStream;
import org.netpreserve.jwarc.WarcTruncationReason;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Input buffer for temporary storage of a HTTP response
 *  This may be in-memory or on-disk, at the discretion of
 *  the implementation.
 * */
public abstract class WarcInputBuffer implements AutoCloseable {
    protected WarcTruncationReason truncationReason = WarcTruncationReason.NOT_TRUNCATED;
    protected Headers headers;
    WarcInputBuffer(Headers headers) {
        this.headers = headers;
    }

    /** If necessary, the stream is closed when the buffer is closed */
    public abstract InputStream read() throws IOException;

    /** The size of the response */
    public abstract int size();

    public final WarcTruncationReason truncationReason() { return truncationReason; }

    public final Headers headers() { return headers; }

    /** Create a buffer for a response.
     *  If the response is small and not compressed, it will be stored in memory.
     *  Otherwise, it will be stored in a temporary file, with compression transparently handled
     *  and suppressed from the headers.
     *  If an error occurs, a buffer will be created with no content and an error status.
     */
    static WarcInputBuffer forResponse(Response rsp) {
        if (rsp == null)
            return new ErrorBuffer();

        try {
            String contentLengthHeader = Objects.requireNonNullElse(rsp.header("Content-Length"), "-1");
            int contentLength = Integer.parseInt(contentLengthHeader);
            String contentEncoding = rsp.header("Content-Encoding");

            if (contentEncoding == null && contentLength > 0 && contentLength < 8192) {
                // If the content is small and not compressed, we can just read it into memory
                return new MemoryBuffer(rsp, contentLength);
            }
            else {
                // Otherwise, we unpack it into a file and read it from there
                return new FileBuffer(rsp);
            }
        }
        catch (Exception ex) {
            return new ErrorBuffer(rsp);
        }

    }

    /** Copy an input stream to an output stream, with a maximum size and time limit */
    protected void copy(InputStream is, OutputStream os) {
        long startTime = System.currentTimeMillis();
        long size = 0;

        byte[] buffer = new byte[8192];

        // Gobble up the BOM if it's there
        is = new BOMInputStream(is);

        while (true) {
            try {
                int n = is.read(buffer);
                if (n < 0) break;
                size += n;
                os.write(buffer, 0, n);

                if (size > WarcRecorder.MAX_SIZE) {
                    truncationReason = WarcTruncationReason.LENGTH;
                    break;
                }

                if (System.currentTimeMillis() - startTime > WarcRecorder.MAX_TIME) {
                    truncationReason = WarcTruncationReason.TIME;
                    break;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}

/** Pseudo-buffer for when we have an error */
class ErrorBuffer extends WarcInputBuffer {
    public ErrorBuffer() {
        super(Headers.of());
        truncationReason = WarcTruncationReason.UNSPECIFIED;
    }

    public ErrorBuffer(Response rsp) {
        super(rsp.headers());
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
    public MemoryBuffer(Response response, int size) {
        super(response.headers());

        var outputStream = new ByteArrayOutputStream(size);

        copy(response.body().byteStream(), outputStream);

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

    public FileBuffer(Response response) throws IOException {
        super(suppressContentEncoding(response.headers()));

        this.tempFile = Files.createTempFile("rsp", ".html");

        if (response.body() == null) {
            truncationReason = WarcTruncationReason.DISCONNECT;
            return;
        }

        if ("gzip".equals(response.header("Content-Encoding"))) {
            try (var out = Files.newOutputStream(tempFile)) {
                copy(new GZIPInputStream(response.body().byteStream()), out);
            }
            catch (Exception ex) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            }
        }
        else {
            try (var out = Files.newOutputStream(tempFile)) {
                copy(response.body().byteStream(), out);
            }
            catch (Exception ex) {
                truncationReason = WarcTruncationReason.UNSPECIFIED;
            }
        }
    }

    private static Headers suppressContentEncoding(Headers headers) {
        var builder = new Headers.Builder();

        headers.toMultimap().forEach((k, values) -> {
            if ("Content-Encoding".equalsIgnoreCase(k)) {
                return;
            }
            if ("Transfer-Encoding".equalsIgnoreCase(k)) {
                return;
            }
            for (var value : values) {
                builder.add(k, value);
            }
        });

        return builder.build();
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