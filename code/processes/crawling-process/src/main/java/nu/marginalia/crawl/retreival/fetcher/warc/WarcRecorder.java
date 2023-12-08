package nu.marginalia.crawl.retreival.fetcher.warc;

import nu.marginalia.crawl.retreival.fetcher.socket.IpInterceptingNetworkInterceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/** Based on JWarc's fetch method, APL 2.0 license
 * <p></p>
 * This class wraps OkHttp's OkHttpClient and records the HTTP request and response in a WARC file,
 * as best is possible given not all the data is available at the same time and needs to
 * be reconstructed.
 */
public class WarcRecorder implements AutoCloseable {
    private static final int MAX_TIME = 30_000;
    private static final int MAX_SIZE = 1024 * 1024 * 10;
    private final WarcWriter writer;
    private final Path warcFile;
    private static final Logger logger = LoggerFactory.getLogger(WarcRecorder.class);

    private ThreadLocal<byte[]> bufferThreadLocal = ThreadLocal.withInitial(() -> new byte[MAX_SIZE]);

    private boolean temporaryFile = false;

    /**
     * Create a new WarcRecorder that will write to the given file
     *
     * @param warcFile The file to write to
     */
    public WarcRecorder(Path warcFile) throws IOException {
        this.warcFile = warcFile;
        this.writer = new WarcWriter(this.warcFile);
    }

    /**
     * Create a new WarcRecorder that will write to a temporary file
     * and delete it when close() is called.
     */
    public WarcRecorder() throws IOException {
        this.warcFile = Files.createTempFile("warc", ".warc.gz");
        this.writer = new WarcWriter(this.warcFile);

        temporaryFile = true;
    }

    public HttpFetchResult fetch(OkHttpClient client, Request request) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        URI uri = request.url().uri();

        WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
        WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

        String ip;
        Instant date = Instant.now();
        long startMillis = date.toEpochMilli();

        var call = client.newCall(request);

        int totalLength = 0;

        WarcTruncationReason truncationReason = null;

        ResponseDataBuffer responseDataBuffer = new ResponseDataBuffer();

        try (var response = call.execute()) {
            var body = response.body();
            InputStream inputStream;

            if (body == null) {
                inputStream = null;
                truncationReason = WarcTruncationReason.DISCONNECT;
            }
            else {
                inputStream = body.byteStream();
            }

            byte[] buf = new byte[8192];

            ip = IpInterceptingNetworkInterceptor.getIpFromResponse(response);

            String responseHeaders = WarcProtocolReconstructor.getResponseHeader(response);

            responseDataBuffer.put(responseHeaders);
            responseDataBuffer.updateDigest(responseDigestBuilder, 0, responseDataBuffer.length());

            int dataStart = responseDataBuffer.pos();

            while (inputStream != null) {
                int remainingLength = responseDataBuffer.remaining();
                if (remainingLength == 0)
                    break;

                int startPos = responseDataBuffer.pos();

                int n = responseDataBuffer.readFrom(inputStream, remainingLength);
                if (n < 0)
                    break;

                responseDataBuffer.updateDigest(responseDigestBuilder, startPos, n);
                responseDataBuffer.updateDigest(payloadDigestBuilder, startPos, n);
                totalLength += n;

                responseDigestBuilder.update(buf, n);
                payloadDigestBuilder.update(buf, n);

                if (MAX_TIME > 0 && System.currentTimeMillis() - startMillis > MAX_TIME) {
                    truncationReason = WarcTruncationReason.TIME;
                    break;
                }
                if (MAX_SIZE > 0 && totalLength >= MAX_SIZE) {
                    truncationReason = WarcTruncationReason.LENGTH;
                    break;
                }
            }

            WarcResponse.Builder responseBuilder = new WarcResponse.Builder(uri)
                    .blockDigest(responseDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_RESPONSE, responseDataBuffer.copyBytes());

            if (ip != null) responseBuilder.ipAddress(InetAddress.getByName(ip));

            responseBuilder.payloadDigest(payloadDigestBuilder.build());

            if (truncationReason != null)
                responseBuilder.truncated(truncationReason);

            // Build and write the response

            long pos = writer.position();

            var warcResponse = responseBuilder.build();
            warcResponse.http(); // force HTTP header to be parsed before body is consumed so that caller can use it
            writer.write(warcResponse);

            // Build and write the request

            WarcDigestBuilder requestDigestBuilder = new WarcDigestBuilder();

            String httpRequestString = WarcProtocolReconstructor.getHttpRequestString(response.request(), uri);

            requestDigestBuilder.update(httpRequestString);

            WarcRequest warcRequest = new WarcRequest.Builder(uri)
                    .blockDigest(requestDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_REQUEST, httpRequestString.getBytes())
                    .concurrentTo(warcResponse.id())
                    .build();
            warcRequest.http(); // force HTTP header to be parsed before body is consumed so that caller can use it
            writer.write(warcRequest);

            return new HttpFetchResult.ResultOk(uri,
                    response.code(),
                    response.headers(),
                    responseDataBuffer.data,
                    dataStart,
                    responseDataBuffer.length() - dataStart);
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch URL {}", uri, ex);
            return new HttpFetchResult.ResultError(ex);
        }
    }


    private class ResponseDataBuffer {
        private final byte[] data;
        private int length = 0;
        private int pos = 0;

        public ResponseDataBuffer() {
            data = bufferThreadLocal.get();
        }

        public int pos() {
            return pos;
        }
        public int length() {
            return length;
        }

        public void put(String s) {
            byte[] bytes = s.getBytes();
            put(bytes, 0, bytes.length);
        }

        private void put(byte[] bytes, int i, int n) {
            System.arraycopy(bytes, i, data, pos, n);
            pos += n;
            length += n;
        }

        public int readFrom(InputStream inputStream, int remainingLength) throws IOException {
            int n = inputStream.read(data, pos, remainingLength);
            if (n > 0) {
                pos += n;
                length += n;
            }
            return n;
        }

        public int remaining() {
            return MAX_SIZE - pos;
        }

        public void updateDigest(WarcDigestBuilder digestBuilder, int startPos, int n) {
            digestBuilder.update(data, startPos, n);
        }

        public byte[] copyBytes() {
            byte[] copy = new byte[length];
            System.arraycopy(data, 0, copy, 0, length);
            return copy;
        }

    }

    public void close() {
        try {
            writer.close();
            if (temporaryFile)
                Files.deleteIfExists(warcFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
