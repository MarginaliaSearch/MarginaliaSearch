package nu.marginalia.crawl.retreival.fetcher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.SneakyThrows;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.crawl.retreival.Cookies;
import nu.marginalia.crawl.retreival.RateLimitException;
import nu.marginalia.crawl.retreival.fetcher.ContentTypeProber.ContentTypeProbeResult;
import nu.marginalia.crawl.retreival.fetcher.socket.*;
import nu.marginalia.crawl.retreival.fetcher.warc.HttpFetchResult;
import static nu.marginalia.crawl.retreival.fetcher.CrawledDocumentFactory.*;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
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
    private final Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();

    private final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();
    private final ContentTypeProber contentTypeProber;

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
        this.contentTypeProber = new ContentTypeProber(userAgent, client);
    }

    public HttpFetcherImpl(@Named("user-agent") String userAgent) {
        this.client = createClient(null, new ConnectionPool());
        this.userAgent = userAgent;
        this.contentTypeProber = new ContentTypeProber(userAgent, client);
    }

    /**
     * Probe the domain to see if it is reachable, attempting to identify which schema to use,
     * and if there are any redirects.  This is done by one or more HEAD requests.
     *
     * @param url The URL to probe.
     * @return The result of the probe, indicating the state and the URL.
     */
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
                                        WarcRecorder warcRecorder,
                                        ContentTags contentTags)
            throws RateLimitException
    {

        // We don't want to waste time and resources on URLs that are not HTML, so if the file ending
        // looks like it might be something else, we perform a HEAD first to check the content type
        if (contentTags.isEmpty() && contentTypeLogic.isUrlLikeBinary(url))
        {
            ContentTypeProbeResult probeResult = contentTypeProber.probeContentType(url);
            switch (probeResult) {
                case ContentTypeProbeResult.Ok(EdgeUrl redirectUrl) -> {
                    url = redirectUrl;
                }
                case ContentTypeProbeResult.BadContentType (String contentType, int statusCode) -> {
                    return createErrorResponse(url, contentType, statusCode,
                            CrawlerDocumentStatus.BAD_CONTENT_TYPE,
                            contentType
                    );
                }
                case ContentTypeProbeResult.Timeout timeout -> {
                    return createTimeoutErrorRsp(url);
                }
                case ContentTypeProbeResult.Exception ex -> {
                    return createErrorFromException(url, ex.ex());
                }
            };
        }

        var getBuilder = new Request.Builder().get();

        getBuilder.url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .addHeader("User-agent", userAgent);

        contentTags.paint(getBuilder);

        HttpFetchResult result = warcRecorder.fetch(client, getBuilder.build());

        if (result instanceof HttpFetchResult.ResultError err) {
            return createErrorFromException(url, err.ex());
        }
        else if (result instanceof HttpFetchResult.ResultOk ok) {
            try {
                return extractBody(url, ok);
            }
            catch (Exception ex) {
                return createErrorFromException(url, ex);
            }
        }
        else {
            throw new IllegalStateException("Unknown result type " + result.getClass());
        }
    }

    private CrawledDocument createErrorFromException(EdgeUrl url, Exception exception) throws RateLimitException {
        return switch (exception) {
            case RateLimitException rle -> throw rle;
            case SocketTimeoutException ex -> createTimeoutErrorRsp(url);
            case UnknownHostException ex -> createUnknownHostError(url);
            case SocketException ex -> createHardErrorRsp(url, ex);
            case ProtocolException ex -> createHardErrorRsp(url, ex);
            case IllegalCharsetNameException ex -> createHardErrorRsp(url, ex);
            case SSLException ex -> createHardErrorRsp(url, ex);
            case EOFException ex -> createHardErrorRsp(url, ex);
            default -> {
                logger.error("Error during fetching", exception);
                yield createHardErrorRsp(url, exception);
            }
        };
    }

    private CrawledDocument extractBody(EdgeUrl url, HttpFetchResult.ResultOk rsp) throws IOException, RateLimitException {

        var responseUrl = new EdgeUrl(rsp.uri());

        if (!Objects.equals(responseUrl.domain, url.domain)) {
            return createRedirectResponse(url, rsp, responseUrl);
        }

        if (rsp.statusCode() == 429) {
            String retryAfter = Objects.requireNonNullElse(rsp.header("Retry-After"), "1000");

            throw new RateLimitException(retryAfter);
        }

        var byteStream = rsp.getInputStream();

        if ("gzip".equals(rsp.header("Content-Encoding"))) {
            byteStream = new GZIPInputStream(byteStream);
        }
        byteStream = new BOMInputStream(byteStream);

        var contentTypeHeader = rsp.header("Content-Type");
        if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        byte[] data = byteStream.readAllBytes(); // size is limited by WarcRecorder

        var contentType = ContentTypeParser.parseContentType(contentTypeHeader, data);
        if (!contentTypeLogic.isAllowableContentType(contentType.contentType())) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        if ("Shift_JIS".equalsIgnoreCase(contentType.charset())) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CHARSET, "");
        }

        if (!isXRobotsTagsPermitted(rsp.allHeaders("X-Robots-Tag"), userAgent)) {
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

        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .headers(rsp.headers().toString())
                .contentType(contentTypeHeader)
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(rsp.statusCode())
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


    @Override
    public SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder) {
        return fetchRobotsForProto("https", recorder, domain)
                .or(() -> fetchRobotsForProto("http", recorder, domain))
                .orElseGet(() -> new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL));
    }

    @Override
    public SitemapRetriever createSitemapRetriever() {
        return new SitemapRetriever();
    }

    private Optional<SimpleRobotRules> fetchRobotsForProto(String proto, WarcRecorder recorder, EdgeDomain domain) {
        try {
            var url = new EdgeUrl(proto, domain, null, "/robots.txt", null);
            return Optional.of(parseRobotsTxt(fetchContent(url, recorder, ContentTags.empty())));
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

