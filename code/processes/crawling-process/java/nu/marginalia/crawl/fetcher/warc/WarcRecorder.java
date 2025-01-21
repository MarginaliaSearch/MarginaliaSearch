package nu.marginalia.crawl.fetcher.warc;

import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.Cookies;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import org.jetbrains.annotations.Nullable;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Based on JWarc's fetch method, APL 2.0 license
 * <p></p>
 * This class wraps HttpClient and records the HTTP request and response in a WARC file,
 * as best is possible given not all the data is available at the same time and needs to
 * be reconstructed.
 */
public class WarcRecorder implements AutoCloseable {
    /** Maximum time we'll wait on a single request */
    static final int MAX_TIME = 30_000;

    /** Maximum (decompressed) size we'll save */
    static final int MAX_SIZE = Integer.getInteger("crawler.maxFetchSize", 10 * 1024 * 1024);

    private final WarcWriter writer;
    private final Path warcFile;
    private static final Logger logger = LoggerFactory.getLogger(WarcRecorder.class);

    private boolean temporaryFile = false;

    // Affix a version string in case we need to change the format in the future
    // in some way
    private final String warcRecorderVersion = "1.0";
    private final Cookies cookies;
    /**
     * Create a new WarcRecorder that will write to the given file
     *
     * @param warcFile The file to write to
     */
    public WarcRecorder(Path warcFile, HttpFetcherImpl fetcher) throws IOException {
        this.warcFile = warcFile;
        this.writer = new WarcWriter(warcFile);
        this.cookies = fetcher.getCookies();
    }

    public WarcRecorder(Path warcFile, Cookies cookies) throws IOException {
        this.warcFile = warcFile;
        this.writer = new WarcWriter(warcFile);
        this.cookies = cookies;
    }

    /**
     * Create a new WarcRecorder that will write to a temporary file
     * and delete it when close() is called.
     */
    public WarcRecorder() throws IOException {
        this.warcFile = Files.createTempFile("warc", ".warc.gz");
        this.writer = new WarcWriter(this.warcFile);
        this.cookies = new Cookies();

        temporaryFile = true;
    }

    public HttpFetchResult fetch(HttpClient client,
                                 java.net.http.HttpRequest request)
            throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException
    {
        URI requestUri = request.uri();

        WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
        WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

        Instant date = Instant.now();

        // Not entirely sure why we need to do this, but keeping it due to Chesterton's Fence
        Map<String, List<String>> extraHeaders = new HashMap<>(request.headers().map());

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch URL {}:  {}", requestUri, ex.getMessage());
            return new HttpFetchResult.ResultException(ex);
        }


        try (WarcInputBuffer inputBuffer = WarcInputBuffer.forResponse(response);
             InputStream inputStream = inputBuffer.read())
        {
            if (cookies.hasCookies()) {
                extraHeaders.put("X-Has-Cookies", List.of("1"));
            }

            byte[] responseHeaders = WarcProtocolReconstructor.getResponseHeader(response, inputBuffer.size()).getBytes(StandardCharsets.UTF_8);

            ResponseDataBuffer responseDataBuffer = new ResponseDataBuffer(inputBuffer.size() + responseHeaders.length);

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
            final URI responseUri = response.uri();

            WarcResponse.Builder responseBuilder = new WarcResponse.Builder(responseUri)
                    .blockDigest(responseDigestBuilder.build())
                    .date(date)
                    .body(MediaType.HTTP_RESPONSE, responseDataBuffer.copyBytes());

            InetAddress inetAddress = InetAddress.getByName(responseUri.getHost());
            responseBuilder.ipAddress(inetAddress);
            responseBuilder.payloadDigest(payloadDigestBuilder.build());
            responseBuilder.truncated(inputBuffer.truncationReason());

            // Build and write the response

            var warcResponse = responseBuilder.build();
            warcResponse.http(); // force HTTP header to be parsed before body is consumed so that caller can use it
            writer.write(warcResponse);

            // Build and write the request

            WarcDigestBuilder requestDigestBuilder = new WarcDigestBuilder();

            byte[] httpRequestString = WarcProtocolReconstructor
                    .getHttpRequestString(
                            response.request().method(),
                            response.request().headers().map(),
                            extraHeaders,
                            requestUri)
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

            if (Duration.between(date, Instant.now()).compareTo(Duration.ofSeconds(9)) > 0
                    && inputBuffer.size() < 2048
                    && !request.uri().getPath().endsWith("robots.txt")) // don't bail on robots.txt
            {
                // Fast detection and mitigation of crawler traps that respond with slow
                // small responses, with a high branching factor

                // Note we bail *after* writing the warc records, this will effectively only
                // prevent link extraction from the document.

                logger.warn("URL {} took too long to fetch ({}s) and was too small for the effort ({}b)",
                        requestUri,
                        Duration.between(date, Instant.now()).getSeconds(),
                        inputBuffer.size()
                );

                return new HttpFetchResult.ResultException(new IOException("Likely crawler trap"));
            }

            return new HttpFetchResult.ResultOk(responseUri,
                    response.statusCode(),
                    inputBuffer.headers(),
                    inetAddress.getHostAddress(),
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

    private void saveOldResponse(EdgeUrl url, String contentType, int statusCode, byte[] documentBody, @Nullable String headers, ContentTags contentTags) {
        try {
            WarcDigestBuilder responseDigestBuilder = new WarcDigestBuilder();
            WarcDigestBuilder payloadDigestBuilder = new WarcDigestBuilder();

            byte[] bytes;

            if (documentBody == null) {
                bytes = new byte[0];
            } else {
                bytes = documentBody;
            }

            // Create a synthesis of custom headers and the original headers
            // to create a new set of headers that will be written to the WARC file.

            StringJoiner syntheticHeadersBuilder = new StringJoiner("\n");

            syntheticHeadersBuilder.add("Content-Type: " + contentType);
            syntheticHeadersBuilder.add("Content-Length: " + bytes.length);
            if (contentTags.etag() != null) {
                syntheticHeadersBuilder.add("ETag: " + contentTags.etag());
            }
            if (contentTags.lastMod() != null) {
                syntheticHeadersBuilder.add("Last-Modified: " + contentTags.lastMod());
            }

            // Grab the headers from the original response and add them to the fake headers if they are not
            // Content-Type, Content-Length, ETag, or Last-Modified
            for (String headerLine : Objects.requireNonNullElse(headers, "").split("\n")) {
                if (headerLine.isBlank()) continue;

                var lowerCase = headerLine.toLowerCase();

                if (lowerCase.startsWith("content-type:")) continue;
                if (lowerCase.startsWith("content-length:")) continue;

                if (contentTags.etag() != null && lowerCase.startsWith("etag:")) continue;
                if (contentTags.lastMod() != null && lowerCase.startsWith("last-modified:")) continue;

                syntheticHeadersBuilder.add(headerLine);
            }

            byte[] header = WarcProtocolReconstructor
                                        .getResponseHeader(syntheticHeadersBuilder.toString(), statusCode)
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

            if (cookies.hasCookies()) {
                builder.addHeader("X-Has-Cookies", "1");
            }

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
    public void writeReferenceCopy(EdgeUrl url, String contentType, int statusCode, byte[] documentBody, @Nullable String headers, ContentTags ctags) {
        saveOldResponse(url, contentType, statusCode, documentBody, headers, ctags);
    }

    public void writeWarcinfoHeader(String ip, EdgeDomain domain, HttpFetcherImpl.DomainProbeResult result) throws IOException {

        Map<String, List<String>> fields = new HashMap<>();
        fields.put("ip", List.of(ip));
        fields.put("software", List.of("search.marginalia.nu/" + warcRecorderVersion));
        fields.put("domain", List.of(domain.toString()));

        switch (result) {
            case HttpFetcherImpl.DomainProbeResult.Redirect redirectDomain:
                fields.put("X-WARC-Probe-Status", List.of("REDIRECT;" + redirectDomain.domain()));
                break;
            case HttpFetcherImpl.DomainProbeResult.Error error:
                fields.put("X-WARC-Probe-Status", List.of(error.status().toString() + ";" + error.desc()));
                break;
            case HttpFetcherImpl.DomainProbeResult.Ok ok:
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

