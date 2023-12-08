package nu.marginalia.crawl.retreival.fetcher.warc;

import nu.marginalia.crawl.retreival.fetcher.socket.IpInterceptingNetworkInterceptor;
import nu.marginalia.model.EdgeDomain;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

/** Based on JWarc's fetch method, APL 2.0 license
 * <p></p>
 * This class wraps OkHttp's OkHttpClient and records the HTTP request and response in a WARC file,
 * as best is possible given not all the data is available at the same time and needs to
 * be reconstructed.
 */
public class WarcRecordingFetcherClient implements AutoCloseable {
    private static final int MAX_TIME = 30_000;
    private static final int MAX_SIZE = 1024 * 1024 * 10;
    private final WarcWriter writer;

    private final EdgeDomain domain;
    private static final Logger logger = LoggerFactory.getLogger(WarcRecordingFetcherClient.class);


    public WarcRecordingFetcherClient(Path warcFile, EdgeDomain domain) throws IOException {
        this.writer = new WarcWriter(warcFile);
        this.domain = domain;
    }

    public Optional<WarcResponse> fetch(OkHttpClient client, Request request) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        URI uri = request.url().uri();

        WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
        WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

        String ip;
        Instant date = Instant.now();
        long startMillis = date.toEpochMilli();

        Path tempFileName = Files.createTempFile(domain.toString(), ".data");

        var call = client.newCall(request);

        int totalLength = 0;

        WarcTruncationReason truncationReason = null;



        try (FileChannel tempFile =
                 (FileChannel) Files.newByteChannel(tempFileName, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var response = call.execute()
        ) {
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
            tempFile.write(ByteBuffer.wrap(responseHeaders.getBytes()));
            responseDigestBuilder.update(responseHeaders);

            while (inputStream != null) {
                int remainingLength;

                if (MAX_SIZE > 0 && MAX_SIZE - totalLength < buf.length) {
                    remainingLength = (MAX_SIZE - totalLength);
                } else {
                    remainingLength = buf.length;
                }

                int n = inputStream.read(buf, 0, remainingLength);
                if (n < 0)
                    break;

                totalLength += n;

                for (int i = 0; i < n; ) {
                    int written = tempFile.write(ByteBuffer.wrap(buf, i, n - i));
                    i += written;
                }

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

            tempFile.position(0);
            WarcResponse.Builder responseBuilder = new WarcResponse.Builder(uri)
                    .blockDigest(responseDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_RESPONSE, tempFile, tempFile.size());

            if (ip != null) responseBuilder.ipAddress(InetAddress.getByName(ip));

            responseBuilder.payloadDigest(payloadDigestBuilder.build());

            if (truncationReason != null)
                responseBuilder.truncated(truncationReason);

            // Build and write the response

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

            return Optional.of(warcResponse);
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch URL {}", uri, ex);
            return Optional.empty();
        }
        finally {
            Files.deleteIfExists(tempFileName);
        }
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
