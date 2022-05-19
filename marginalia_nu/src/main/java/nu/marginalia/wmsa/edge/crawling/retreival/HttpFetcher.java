package nu.marginalia.wmsa.edge.crawling.retreival;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawler.domain.LinkParser;
import nu.marginalia.wmsa.edge.crawler.fetcher.ContentTypeParser;
import nu.marginalia.wmsa.edge.crawler.fetcher.Cookies;
import nu.marginalia.wmsa.edge.crawler.fetcher.NoSecuritySSL;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgent;
    private final int maxFetchSize = 1024*512;
    private Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();

    private final LinkParser linkParser = new LinkParser();

    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        this.allowAllContentTypes = allowAllContentTypes;
    }

    private boolean allowAllContentTypes = false;

    private final OkHttpClient client;

    public enum FetchResultState {
        OK,
        REDIRECT,
        ERROR;
    };

    @AllArgsConstructor @ToString
    public static class FetchResult {
        public final FetchResultState state;
        public final EdgeDomain domain;

        public boolean ok() {
            return state == FetchResultState.OK;
        }
    };

    @SneakyThrows
    private OkHttpClient createClient(Dispatcher dispatcher) {
        return new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .sslSocketFactory(NoSecuritySSL.buildSocketFactory(), (X509TrustManager) NoSecuritySSL.trustAllCerts[0])
            .hostnameVerifier(NoSecuritySSL.buildHostnameVerifyer())
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
    public HttpFetcher(@Named("user-agent") String userAgent, Dispatcher dispatcher) {
        this.client = createClient(dispatcher);
        this.userAgent = userAgent;
    }

    @SneakyThrows
    public FetchResult probeDomain(EdgeUrl url) {
        var head = new Request.Builder().head().addHeader("User-agent", userAgent)
                .url(new EdgeUrl(url.proto, url.domain, url.port, "/").toString())
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
    public CrawledDocument fetchContent(EdgeUrl url) {
        if (isUrlLikeBinary(url)) {

            logger.debug("Probing suspected binary {}", url);

            var head = createHeadRequest(url);
            var call = client.newCall(head);

            try (var rsp = call.execute()) {
                var contentTypeHeader = rsp.header("Content-type");
                if (contentTypeHeader != null && !isAllowableContentType(contentTypeHeader)) {
                    return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "Early probe failed");
                }
            }
            catch (Exception ex) {
                return createHardErrorRsp(url, ex);
            }
        }

        var get = createGetRequest(url);
        var call = client.newCall(get);




        try (var rsp = call.execute()) {
            return extractBody(url, rsp);
        }
        catch (Exception ex) {
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

    private CrawledDocument extractBody(EdgeUrl url, Response rsp) throws IOException, URISyntaxException {

        var responseUrl = new EdgeUrl(rsp.request().url().toString());
        if (!responseUrl.equals(url)) {
            return createRedirectResponse(url, rsp, responseUrl);
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
        if (contentTypeHeader != null && !isAllowableContentType(contentTypeHeader)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        byte[] data = byteStream.readNBytes(maxFetchSize);

        var contentType = ContentTypeParser.parse(contentTypeHeader, data);
        if (!isAllowableContentType(contentType.contentType)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }

        if ("Shift_JIS".equalsIgnoreCase(contentType.charset)) {
            return createErrorResponse(url, rsp, CrawlerDocumentStatus.BAD_CHARSET, "");
        }

        var strData = new String(data, Charset.forName(contentType.charset));
        var canonical = rsp.header("rel=canonical", "");

        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .headers(rsp.headers().toString())
                .contentType(rsp.header("Content-type"))
                .timestamp(LocalDateTime.now().toString())
                .canonicalUrl(canonical)
                .httpStatus(rsp.code())
                .url(url.toString())
                .documentBody(strData)
                .build();
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


    private final Predicate<String> probableHtmlPattern = Pattern.compile("^.*\\.(htm|html|php|txt)(\\?.*)?$").asPredicate();
    private final Predicate<String> probableBinaryPattern = Pattern.compile("^.*\\.[a-z]+$").asPredicate();

    public boolean isUrlLikeBinary(EdgeUrl url) {
        String urlString = url.toString().toLowerCase();

        return (!probableHtmlPattern.test(urlString) && probableBinaryPattern.test(urlString));
    }

    private boolean isAllowableContentType(String contentType) {
        return allowAllContentTypes || contentType.startsWith("text")
                || contentType.startsWith("application/xhtml")
                || contentType.startsWith("application/xml")
                || contentType.startsWith("application/atom+xml")
                || contentType.startsWith("application/rss+xml")
                || contentType.startsWith("application/x-rss+xml")
                || contentType.startsWith("application/rdf+xml")
                || contentType.startsWith("x-rss+xml");
    }

    public SimpleRobotRules fetchRobotRules(EdgeDomain domain) {
        return fetchRobotsForProto("https", domain)
                .or(() -> fetchRobotsForProto("http", domain))
                .orElseGet(() -> new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL));
    }

    private Optional<SimpleRobotRules> fetchRobotsForProto(String proto, EdgeDomain domain) {
        try {
            var url = new EdgeUrl(proto, domain, null, "/robots.txt");
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
