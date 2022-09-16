package nu.marginalia.wmsa.edge.crawling.retreival;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.crawling.retreival.logic.ContentTypeLogic;
import nu.marginalia.wmsa.edge.crawling.retreival.logic.ContentTypeParser;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeContentType;
import okhttp3.*;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgent;
    private final int maxFetchSize = 1024*512;
    private final Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();

    private final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        contentTypeLogic.setAllowAllContentTypes(allowAllContentTypes);
    }

    private final OkHttpClient client;

    public enum FetchResultState {
        OK,
        REDIRECT,
        ERROR
    }

    @AllArgsConstructor @ToString
    public static class FetchResult {
        public final FetchResultState state;
        public final EdgeDomain domain;

        public boolean ok() {
            return state == FetchResultState.OK;
        }
    }

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
            .connectionPool(pool)
            .cookieJar(cookies.getJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    }

    public List<String> getCookies() {
        return cookies.getCookies();
    }

    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcher(@Named("user-agent") String userAgent, Dispatcher dispatcher, ConnectionPool connectionPool) {
        this.client = createClient(dispatcher, connectionPool);
        this.userAgent = userAgent;
    }

    public HttpFetcher(@Named("user-agent") String userAgent) {
        this.client = createClient(null, new ConnectionPool());
        this.userAgent = userAgent;
    }

    @SneakyThrows
    public FetchResult probeDomain(EdgeUrl url) {
        var head = new Request.Builder().head().addHeader("User-agent", userAgent)
                .url(url.toString())
                .build();

        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            var requestUrl = rsp.request().url().toString();
            EdgeDomain requestDomain = new EdgeUrl(requestUrl).domain;

            if (!Objects.equals(requestDomain, url.domain)) {
                return new FetchResult(FetchResultState.REDIRECT, requestDomain);
            }
            return new FetchResult(FetchResultState.OK, requestDomain);
        }
        catch (Exception ex) {
            logger.debug("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());
            return new FetchResult(FetchResultState.ERROR, url.domain);
        }
    }

    private Request createHeadRequest(EdgeUrl url) {
        return new Request.Builder().head().addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .build();
    }

    private Request createGetRequest(EdgeUrl url) {
        return new Request.Builder().get().addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .build();

    }

    @SneakyThrows
    public CrawledDocument fetchContent(EdgeUrl url) throws RateLimitException {

        if (contentTypeLogic.isUrlLikeBinary(url)) {
            logger.debug("Probing suspected binary {}", url);

            var head = createHeadRequest(url);
            var call = client.newCall(head);

            try (var rsp = call.execute()) {
                var contentTypeHeader = rsp.header("Content-type");
                if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                    return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "Early probe failed");
                }
            }
            catch (SocketTimeoutException ex) {
                return createTimeoutErrorRsp(url, ex);
            }
            catch (Exception ex) {
                logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());
                return createHardErrorRsp(url, ex);
            }
        }

        var get = createGetRequest(url);
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
        catch (IllegalCharsetNameException ex) {
            return createHardErrorRsp(url, ex);
        }
        catch (Exception ex) {
            logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());
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
        if (null == byteStream) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.ERROR, "No body");
        }
        if ("gzip".equals(rsp.header("Content-encoding"))) {
            byteStream = new GZIPInputStream(byteStream);
        }
        byteStream = new BOMInputStream(byteStream);

        var contentTypeHeader = rsp.header("Content-type");
        if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        byte[] data = byteStream.readNBytes(maxFetchSize);

        var contentType = ContentTypeParser.parse(contentTypeHeader, data);
        if (!contentTypeLogic.isAllowableContentType(contentType.contentType)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        if ("Shift_JIS".equalsIgnoreCase(contentType.charset)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CHARSET, "");
        }

        var strData = getStringData(data, contentType);
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

    private String getStringData(byte[] data, EdgeContentType contentType) {
        Charset charset;
        try {
            charset = Charset.forName(contentType.charset);
        }
        catch (IllegalCharsetNameException ex) {
            charset = StandardCharsets.UTF_8;
        }
        catch (UnsupportedCharsetException ex) {
            // This is usually like Macintosh Latin
            // (https://en.wikipedia.org/wiki/Macintosh_Latin_encoding)
            //
            // It's close enough to 8859-1 to serve
            charset = StandardCharsets.ISO_8859_1;
        }
        return new String(data, charset);
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

    public SimpleRobotRules fetchRobotRules(EdgeDomain domain) {
        return fetchRobotsForProto("https", domain)
                .or(() -> fetchRobotsForProto("http", domain))
                .orElseGet(() -> new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL));
    }

    private Optional<SimpleRobotRules> fetchRobotsForProto(String proto, EdgeDomain domain) {
        try {
            var url = new EdgeUrl(proto, domain, null, "/robots.txt", null);
            return Optional.of(parseRobotsTxt(fetchContent(url)));
        }
        catch (Exception ex) {
            return Optional.empty();
        }
    }

    private SimpleRobotRules parseRobotsTxt(CrawledDocument doc) {
        return robotsParser.parseContent(doc.url,
                doc.documentBody.getBytes(StandardCharsets.UTF_8),
                doc.contentType,
                userAgent);
    }

}
