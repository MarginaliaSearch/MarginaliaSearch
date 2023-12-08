package nu.marginalia.crawl.retreival.fetcher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.SneakyThrows;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.crawl.retreival.Cookies;
import nu.marginalia.crawl.retreival.RateLimitException;
import nu.marginalia.crawl.retreival.fetcher.socket.*;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.crawl.retreival.logic.ContentTypeLogic;
import nu.marginalia.contenttype.ContentTypeParser;
import okhttp3.*;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;
import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.IllegalCharsetNameException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class HttpFetcherImpl implements HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgent;
    private final int maxFetchSize = 1024*512;
    private final Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();

    private final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    @Override
    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        contentTypeLogic.setAllowAllContentTypes(allowAllContentTypes);
    }

    private final OkHttpClient client;

    private static final FastTerminatingSocketFactory ftSocketFactory = new FastTerminatingSocketFactory();

    @SneakyThrows
    private OkHttpClient createClient(Dispatcher dispatcher, ConnectionPool pool) {
        var builder = new OkHttpClient.Builder();
        if (dispatcher != null) {
            builder.dispatcher(dispatcher);
        }

        return builder.sslSocketFactory(NoSecuritySSL.buildSocketFactory(), (X509TrustManager) NoSecuritySSL.trustAllCerts[0])
            .socketFactory(ftSocketFactory)
            .hostnameVerifier(NoSecuritySSL.buildHostnameVerifyer())
            .addNetworkInterceptor(new IpInterceptingNetworkInterceptor())
            .connectionPool(pool)
            .cookieJar(cookies.getJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    }

    @Override
    public List<String> getCookies() {
        return cookies.getCookies();
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcherImpl(@Named("user-agent") String userAgent, Dispatcher dispatcher, ConnectionPool connectionPool) {
        this.client = createClient(dispatcher, connectionPool);
        this.userAgent = userAgent;
    }

    public HttpFetcherImpl(@Named("user-agent") String userAgent) {
        this.client = createClient(null, new ConnectionPool());
        this.userAgent = userAgent;
    }

    @Override
    @SneakyThrows
    public FetchResult probeDomain(EdgeUrl url) {
        var head = new Request.Builder().head().addHeader("User-agent", userAgent)
                .url(url.toString())
                .build();

        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            EdgeUrl requestUrl = new EdgeUrl(rsp.request().url().toString());

            if (!Objects.equals(requestUrl.domain, url.domain)) {
                return new FetchResult(FetchResultState.REDIRECT, requestUrl);
            }
            return new FetchResult(FetchResultState.OK, requestUrl);
        }

        catch (Exception ex) {
            if (url.proto.equalsIgnoreCase("http") && "/".equals(url.path)) {
                return probeDomain(new EdgeUrl("https", url.domain, url.port, url.path, url.param));
            }

            logger.info("Error during fetching", ex);
            return new FetchResult(FetchResultState.ERROR, url);
        }
    }


    @Override
    @SneakyThrows
    public CrawledDocument fetchContent(EdgeUrl url,
                                        ContentTags contentTags)
            throws RateLimitException
    {

        // We don't want to waste time and resources on URLs that are not HTML, so if the file ending
        // looks like it might be something else, we perform a HEAD first to check the content type
        if (contentTags.isEmpty() && contentTypeLogic.isUrlLikeBinary(url))
        {
            logger.debug("Probing suspected binary {}", url);

            var headBuilder = new Request.Builder().head()
                    .addHeader("User-agent", userAgent)
                    .addHeader("Accept-Encoding", "gzip")
                    .url(url.toString());

            var head = headBuilder.build();
            var call = client.newCall(head);

            try (var rsp = call.execute()) {
                var contentTypeHeader = rsp.header("Content-type");
                if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                    return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "Early probe failed");
                }

                // Update the URL to the final URL of the HEAD request, otherwise we might end up doing

                // HEAD 301 url1 -> url2
                // HEAD 200 url2
                // GET 301 url1 -> url2
                // GET 200 url2

                // which is not what we want. Overall we want to do as few requests as possible to not raise
                // too many eyebrows when looking at the logs on the target server.  Overall it's probably desirable
                // that it looks like the traffic makes sense, as opposed to looking like a broken bot.

                var redirectUrl = new EdgeUrl(rsp.request().url().toString());
                if (Objects.equals(redirectUrl.domain, url.domain))
                    url = redirectUrl;
            }
            catch (SocketTimeoutException ex) {
                return createTimeoutErrorRsp(url, ex);
            }
            catch (Exception ex) {
                logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());
                return createHardErrorRsp(url, ex);
            }
        }

        var getBuilder = new Request.Builder().get();

        getBuilder.addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip");

        contentTags.paint(getBuilder);

        var get = getBuilder.build();
        var call = client.newCall(get);

        try (var rsp = call.execute()) {
            return extractBody(url, rsp);
        }
        catch (RateLimitException rle) {
            throw rle;
        }
        catch (SocketTimeoutException ex) {
            return createTimeoutErrorRsp(url, ex);
        }
        catch (UnknownHostException ex) {
            return createUnknownHostError(url, ex);
        }
        catch (SocketException | ProtocolException | IllegalCharsetNameException | SSLException | EOFException ex) {
            // This is a bit of a grab-bag of errors that crop up
            // IllegalCharsetName is egg on our face,
            // but SSLException and EOFException are probably the server's fault

            return createHardErrorRsp(url, ex);
        }
        catch (Exception ex) {
            logger.error("Error during fetching", ex);
            return createHardErrorRsp(url, ex);
        }
    }

    private CrawledDocument createHardErrorRsp(EdgeUrl url, Exception why) {
        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.ERROR.toString())
                .crawlerStatusDesc(why.getClass().getSimpleName() + ": " + why.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }

    private CrawledDocument createUnknownHostError(EdgeUrl url, Exception why) {
        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.ERROR.toString())
                .crawlerStatusDesc("Unknown Host")
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }

    private CrawledDocument createTimeoutErrorRsp(EdgeUrl url, Exception why) {
        return CrawledDocument.builder()
                .crawlerStatus("Timeout")
                .crawlerStatusDesc(why.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }
    private CrawledDocument createErrorResponse(EdgeUrl url, Response rsp, CrawlerDocumentStatus status, String why) {
        return CrawledDocument.builder()
                .crawlerStatus(status.toString())
                .crawlerStatusDesc(why)
                .headers(rsp.headers().toString())
                .contentType(rsp.header("Content-type"))
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(rsp.code())
                .url(url.toString())
                .build();
    }

    private CrawledDocument extractBody(EdgeUrl url, Response rsp) throws IOException, URISyntaxException, RateLimitException {

        var responseUrl = new EdgeUrl(rsp.request().url().toString());
        if (!Objects.equals(responseUrl.domain, url.domain)) {
            return createRedirectResponse(url, rsp, responseUrl);
        }

        if (rsp.code() == 429) {
            throw new RateLimitException(rsp.header("Retry-After", "1000"));
        }

        var body = rsp.body();
        if (null == body) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.ERROR, "No body");
        }

        var byteStream = body.byteStream();

        if ("gzip".equals(rsp.header("Content-encoding"))) {
            byteStream = new GZIPInputStream(byteStream);
        }
        byteStream = new BOMInputStream(byteStream);

        var contentTypeHeader = rsp.header("Content-type");
        if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        byte[] data = byteStream.readNBytes(maxFetchSize);

        var contentType = ContentTypeParser.parseContentType(contentTypeHeader, data);
        if (!contentTypeLogic.isAllowableContentType(contentType.contentType())) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        if ("Shift_JIS".equalsIgnoreCase(contentType.charset())) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CHARSET, "");
        }

        if (!isXRobotsTagsPermitted(rsp.headers("X-Robots-Tag"), userAgent)) {
            return CrawledDocument.builder()
                    .crawlerStatus(CrawlerDocumentStatus.ROBOTS_TXT.name())
                    .crawlerStatusDesc("X-Robots-Tag")
                    .url(responseUrl.toString())
                    .httpStatus(-1)
                    .timestamp(LocalDateTime.now().toString())
                    .headers(rsp.headers().toString())
                    .build();
        }

        var strData = DocumentBodyToString.getStringData(contentType, data);

        var canonical = rsp.header("rel=canonical", "");

        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .headers(rsp.headers().toString())
                .contentType(rsp.header("Content-type"))
                .timestamp(LocalDateTime.now().toString())
                .canonicalUrl(canonical)
                .httpStatus(rsp.code())
                .url(responseUrl.toString())
                .documentBody(strData)
                .build();
    }

    /**  Check X-Robots-Tag header tag to see if we are allowed to index this page.
     * <p>
     * Reference: <a href="https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag">https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag</a>
     *
     * @param xRobotsHeaderTags List of X-Robots-Tag values
     * @param userAgent User agent string
     * @return true if we are allowed to index this page
     */
    // Visible for tests
    public static boolean isXRobotsTagsPermitted(List<String> xRobotsHeaderTags, String userAgent) {
        boolean isPermittedGeneral = true;
        boolean isPermittedMarginalia = false;
        boolean isForbiddenMarginalia = false;

        for (String header : xRobotsHeaderTags) {
            if (header.indexOf(':') >= 0) {
                String[] parts = StringUtils.split(header, ":", 2);

                if (parts.length < 2)
                    continue;

                // Is this relevant to us?
                if (!Objects.equals(parts[0].trim(), userAgent))
                    continue;

                if (parts[1].contains("noindex"))
                    isForbiddenMarginalia = true;
                else if (parts[1].contains("none"))
                    isForbiddenMarginalia = true;
                else if (parts[1].contains("all"))
                    isPermittedMarginalia = true;
            }
            else {
                if (header.contains("noindex"))
                    isPermittedGeneral = false;
                if (header.contains("none"))
                    isPermittedGeneral = false;
            }
        }

        if (isPermittedMarginalia)
            return true;
        if (isForbiddenMarginalia)
            return false;
        return isPermittedGeneral;
    }

    private CrawledDocument createRedirectResponse(EdgeUrl url, Response rsp, EdgeUrl responseUrl) {

        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.REDIRECT.name())
                .redirectUrl(responseUrl.toString())
                .headers(rsp.headers().toString())
                .contentType(rsp.header("Content-type"))
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(rsp.code())
                .url(url.toString())
                .build();

    }

    @Override
    public SimpleRobotRules fetchRobotRules(EdgeDomain domain) {
        return fetchRobotsForProto("https", domain)
                .or(() -> fetchRobotsForProto("http", domain))
                .orElseGet(() -> new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL));
    }

    @Override
    public SitemapRetriever createSitemapRetriever() {
        return new SitemapRetriever();
    }

    private Optional<SimpleRobotRules> fetchRobotsForProto(String proto, EdgeDomain domain) {
        try {
            var url = new EdgeUrl(proto, domain, null, "/robots.txt", null);
            return Optional.of(parseRobotsTxt(fetchContent(url, ContentTags.empty())));
        }
        catch (Exception ex) {
            return Optional.empty();
        }
    }

    private SimpleRobotRules parseRobotsTxt(CrawledDocument doc) {
        return robotsParser.parseContent(doc.url,
                doc.documentBody.getBytes(),
                doc.contentType,
                userAgent);
    }

}
