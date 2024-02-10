package nu.marginalia.crawl.retreival.fetcher.warc;

import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawling.body.HttpFetchResult;
import nu.marginalia.crawl.retreival.fetcher.socket.IpInterceptingNetworkInterceptor;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Based on JWarc's fetch method, APL 2.0 license
 * <p></p>
 * This class wraps OkHttp's OkHttpClient and records the HTTP request and response in a WARC file,
 * as best is possible given not all the data is available at the same time and needs to
 * be reconstructed.
 */
public class WarcRecorder implements AutoCloseable {
    /** Maximum time we'll wait on a single request */
    static final int MAX_TIME = 30_000;
    /** Maximum (decompressed) size we'll fetch */
    static final int MAX_SIZE = 1024 * 1024 * 10;

    private final WarcWriter writer;
    private final Path warcFile;
    private static final Logger logger = LoggerFactory.getLogger(WarcRecorder.class);

    private boolean temporaryFile = false;

    // Affix a version string in case we need to change the format in the future
    // in some way
    private final String warcRecorderVersion = "1.0";

    // We need to know if the site uses cookies so this can be reported among the search results
    // -- flip this to true if we see any cookies.  This information will also be painted on any
    // revisited pages.  It's not 100% perfect and a bit order dependent, but it's good enough.
    private final WarcXCookieInformationHeader cookieInformation = new WarcXCookieInformationHeader();

    /**
     * Create a new WarcRecorder that will write to the given file
     *
     * @param warcFile The file to write to
     */
    public WarcRecorder(Path warcFile) throws IOException {
        this.warcFile = warcFile;
        this.writer = new WarcWriter(warcFile);
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

    public HttpFetchResult fetch(OkHttpClient client, Request request) throws NoSuchAlgorithmException,
            IOException,
            URISyntaxException,
            InterruptedException
    {
        URI requestUri = request.url().uri();

        WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
        WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

        String ip;
        Instant date = Instant.now();

        var call = client.newCall(request);

        cookieInformation.update(client, request.url());

        try (var response = call.execute();
             WarcInputBuffer inputBuffer = WarcInputBuffer.forResponse(response))
        {
            byte[] responseHeaders = WarcProtocolReconstructor.getResponseHeader(response, inputBuffer.size()).getBytes(StandardCharsets.UTF_8);

            ResponseDataBuffer responseDataBuffer = new ResponseDataBuffer(inputBuffer.size() + responseHeaders.length);
            InputStream inputStream = inputBuffer.read();

            ip = IpInterceptingNetworkInterceptor.getIpFromResponse(response);

            responseDataBuffer.put(responseHeaders);
            responseDataBuffer.updateDigest(responseDigestBuilder, 0, responseHeaders.length);

            int dataStart = responseDataBuffer.pos();

            for (;;) {
                int remainingLength = responseDataBuffer.remaining();
                if (remainingLength == 0)
                    break;

                int startPos = responseDataBuffer.pos();

                int n = responseDataBuffer.readFrom(inputStream, remainingLength);
                if (n < 0)
                    break;

                responseDataBuffer.updateDigest(responseDigestBuilder, startPos, n);
                responseDataBuffer.updateDigest(payloadDigestBuilder, startPos, n);
            }

            // It looks like this might be the same as requestUri, but it's not;
            // it's the URI after resolving redirects.
            final URI responseUri = response.request().url().uri();

            WarcResponse.Builder responseBuilder = new WarcResponse.Builder(responseUri)
                    .blockDigest(responseDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_RESPONSE, responseDataBuffer.copyBytes());

            cookieInformation.paint(responseBuilder);

            if (ip != null) responseBuilder.ipAddress(InetAddress.getByName(ip));

            responseBuilder.payloadDigest(payloadDigestBuilder.build());
            responseBuilder.truncated(inputBuffer.truncationReason());

            // Build and write the response

            var warcResponse = responseBuilder.build();
            warcResponse.http(); // force HTTP header to be parsed before body is consumed so that caller can use it
            writer.write(warcResponse);

            // Build and write the request

            WarcDigestBuilder requestDigestBuilder = new WarcDigestBuilder();

            byte[] httpRequestString = WarcProtocolReconstructor
                    .getHttpRequestString(response.request(), requestUri)
                    .getBytes();

            requestDigestBuilder.update(httpRequestString);

            WarcRequest warcRequest = new WarcRequest.Builder(requestUri)
                    .blockDigest(requestDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_REQUEST, httpRequestString)
                    .concurrentTo(warcResponse.id())
                    .build();

            warcRequest.http(); // force HTTP header to be parsed before body is consumed so that caller can use it
            writer.write(warcRequest);

            return new HttpFetchResult.ResultOk(responseUri,
                    response.code(),
                    inputBuffer.headers(),
                    ip,
                    responseDataBuffer.data,
                    dataStart,
                    responseDataBuffer.length() - dataStart);
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch URL {}:  {}", requestUri, ex.getMessage());
            return new HttpFetchResult.ResultException(ex);
        }
    }

    public void resync(WarcRecord item) throws IOException {
        writer.write(item);
    }

    private void saveOldResponse(EdgeUrl url, String contentType, int statusCode, String documentBody, ContentTags contentTags) {
        try {
            WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
            WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

            byte[] bytes;

            if (documentBody == null) {
                bytes = new byte[0];
            }
            else {
                bytes = documentBody.getBytes();
            }

            StringJoiner fakeHeadersBuilder = new StringJoiner("\n");

            fakeHeadersBuilder.add(STR."Content-Type: \{contentType}");
            fakeHeadersBuilder.add(STR."Content-Length: \{bytes.length}");
            if (contentTags.etag() != null) {
                fakeHeadersBuilder.add(STR."ETag: \{contentTags.etag()}");
            }
            if (contentTags.lastMod() != null) {
                fakeHeadersBuilder.add(STR."Last-Modified: \{contentTags.lastMod()}");
            }

            byte[] header = WarcProtocolReconstructor
                                        .getResponseHeader(fakeHeadersBuilder.toString(), statusCode)
                                        .getBytes(StandardCharsets.UTF_8);
            ResponseDataBuffer responseDataBuffer = new ResponseDataBuffer(bytes.length + header.length);
            responseDataBuffer.put(header);

            responseDigestBuilder.update(header);

            responseDigestBuilder.update(bytes, bytes.length);
            payloadDigestBuilder.update(bytes, bytes.length);
            responseDataBuffer.put(bytes, 0, bytes.length);

            WarcXResponseReference.Builder builder = new WarcXResponseReference.Builder(url.asURI())
                    .blockDigest(responseDigestBuilder.build())
                    .payloadDigest(payloadDigestBuilder.build())
                    .date(Instant.now())
                    .body(MediaType.HTTP_RESPONSE, responseDataBuffer.copyBytes());

            cookieInformation.paint(builder);

            var reference = builder.build();

            reference.http(); // force HTTP header to be parsed before body is consumed so that caller can use it

            writer.write(reference);

        } catch (URISyntaxException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write a reference copy of the given document data.  This is used when the crawler provides
     * an E-Tag or Last-Modified header, and the server responds with a 304 Not Modified.  In this
     * scenario we want to record the data as it was in the previous crawl, but not re-fetch it.
     */
    public void writeReferenceCopy(EdgeUrl url, String contentType, int statusCode, String documentBody, ContentTags ctags) {
        saveOldResponse(url, contentType, statusCode, documentBody, ctags);
    }

    public void writeWarcinfoHeader(String ip, EdgeDomain domain, DomainProber.ProbeResult result) throws IOException {

        Map<String, List<String>> fields = new HashMap<>();
        fields.put("ip", List.of(ip));
        fields.put("software", List.of(STR."search.marginalia.nu/\{warcRecorderVersion}"));
        fields.put("domain", List.of(domain.toString()));

        switch (result) {
            case DomainProber.ProbeResultRedirect redirectDomain:
                fields.put("X-WARC-Probe-Status", List.of(STR."REDIRECT;\{redirectDomain.domain()}"));
                break;
            case DomainProber.ProbeResultError error:
                fields.put("X-WARC-Probe-Status", List.of(STR."\{error.status().toString()};\{error.desc()}"));
                break;
            case DomainProber.ProbeResultOk ok:
                fields.put("X-WARC-Probe-Status", List.of("OK"));
                break;
        }

        var warcinfo = new Warcinfo.Builder()
                .date(Instant.now())
                .fields(fields)
                .recordId(UUID.randomUUID())
                .build();

        writer.write(warcinfo);
    }

    public void flagAsRobotsTxtError(EdgeUrl top) {
        try {
            WarcXEntityRefused refusal = new WarcXEntityRefused.Builder(top.asURI(), WarcXEntityRefused.documentRobotsTxtSkippedURN)
                    .date(Instant.now())
                    .build();

            writer.write(refusal);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void flagAsFailedContentTypeProbe(EdgeUrl url, String contentType, int status) {
        try {
            WarcXEntityRefused refusal = new WarcXEntityRefused.Builder(url.asURI(), WarcXEntityRefused.documentBadContentTypeURN)
                    .date(Instant.now())
                    .addHeader("Rejected-Content-Type", contentType)
                    .addHeader("Http-Status", Integer.toString(status))
                    .build();

            writer.write(refusal);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void flagAsError(EdgeUrl url, Exception ex) {
        try {
            WarcXEntityRefused refusal = new WarcXEntityRefused.Builder(url.asURI(), WarcXEntityRefused.documentUnspecifiedError)
                    .date(Instant.now())
                    .addHeader("Exception", ex.getClass().getSimpleName())
                    .addHeader("ErrorMessage", Objects.requireNonNullElse(ex.getMessage(), ""))
                    .build();

            writer.write(refusal);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void flagAsTimeout(EdgeUrl url) {
        try {
            WarcXEntityRefused refusal = new WarcXEntityRefused.Builder(url.asURI(), WarcXEntityRefused.documentProbeTimeout)
                    .date(Instant.now())
                    .build();

            writer.write(refusal);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ResponseDataBuffer {
        private final byte[] data;
        private int length = 0;
        private int pos = 0;

        public ResponseDataBuffer(int size) {
            data = new byte[size];
        }

        public int pos() {
            return pos;
        }
        public int length() {
            return length;
        }

        public void put(byte[] bytes) {
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
            return data.length - pos;
        }

        public void updateDigest(WarcDigestBuilder digestBuilder, int startPos, int n) {
            digestBuilder.update(data, startPos, n);
        }

        public byte[] copyBytes() {
            if (length < data.length)
                return Arrays.copyOf(data, length);
            else
                return data;
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

