package nu.marginalia.wmsa.edge.crawler.fetcher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import nu.marginalia.wmsa.client.exception.NetworkException;
import nu.marginalia.wmsa.edge.crawler.domain.LinkParser;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgent;
    private final int maxFetchSize = 1024*512;
    private Cookies cookies = new Cookies();

    private final LinkParser linkParser = new LinkParser();

    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        this.allowAllContentTypes = allowAllContentTypes;
    }

    private boolean allowAllContentTypes = false;

    private final OkHttpClient client = createClient();

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
    private OkHttpClient createClient() {
        return new OkHttpClient.Builder()
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

    public boolean hasCookies() {
        return cookies.hasCookies();
    }

    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcher(@Named("user-agent") String userAgent) {
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

    @SneakyThrows
    public EdgeRawPageContents fetchContent(EdgeUrl url) {
        if (isUrlLikeBinary(url) && !probeContentType(url)) {
            return null;
        }

        var get = new Request.Builder().get().addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .build();

        var call = client.newCall(get);

        try (var rsp = call.execute()) {
            if (rsp.code() >= 400) {
                throw new NetworkException("Bad status " + rsp.code());
            }
            return extractBody(url, rsp);
        }
    }

    private final Predicate<String> probableHtmlPattern = Pattern.compile("^.*\\.(htm|html|php|txt)(\\?.*)?$").asPredicate();
    private final Predicate<String> probableBinaryPattern = Pattern.compile("^.*\\.[a-z]+$").asPredicate();

    public boolean isUrlLikeBinary(EdgeUrl url) {
        String urlString = url.toString().toLowerCase();

        return (!probableHtmlPattern.test(urlString) && probableBinaryPattern.test(urlString));
    }

    @SneakyThrows
    private boolean probeContentType(EdgeUrl url) {
        logger.debug("Probing suspected binary {}", url);

        var head = new Request.Builder().get().addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .build();

        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            if (rsp.code() >= 400) {
                throw new NetworkException("Bad status " + rsp.code());
            }
            var contentTypeHeader = rsp.header("Content-type");
            if (contentTypeHeader != null && !isAllowableContentType(contentTypeHeader)) {
                return false;
            }
        }

        return true;
    }

    @SneakyThrows
    private EdgeRawPageContents extractBody(EdgeUrl url, Response response) {
        try {
            var body = response.body();
            if (null == body) {
                throw new NetworkException("No body in response");
            }

            var byteStream = body.byteStream();
            if (null == byteStream) {
                throw new NetworkException("No body in response");
            }
            if ("gzip".equals(response.header("Content-encoding"))) {
                byteStream = new GZIPInputStream(byteStream);
            }
            byteStream = new BOMInputStream(byteStream);

            var contentTypeHeader = response.header("Content-type");
            if (contentTypeHeader != null && !isAllowableContentType(contentTypeHeader)) {
                throw new BadContentType(contentTypeHeader);
            }

            byte[] data = byteStream.readNBytes(maxFetchSize);

            var contentType = ContentTypeParser.parse(contentTypeHeader, data);
            if (!isAllowableContentType(contentType.contentType)) {
                throw new BadContentType(contentType.contentType);
            }

            if ("Shift_JIS".equalsIgnoreCase(contentType.charset)) {
                throw new BadContentType(contentType.contentType);
            }

            var strData = new String(data, Charset.forName(contentType.charset));

            return new EdgeRawPageContents(url,
                    getRedirectUrl(url, response),
                    strData,
                    contentType,
                    InetAddress.getByName(url.domain.getAddress()).getHostAddress(),
                    hasCookies(),
                    LocalDateTime.now().toString());
        }
        catch (IOException ex) {
            throw new NetworkException(ex);
        }
    }

    private EdgeUrl getRedirectUrl(EdgeUrl url, Response response) throws URISyntaxException {

        final String canonicalHeader = response.header("rel=canonical");
        if (null != canonicalHeader) {
            var ret = linkParser.parseLink(url, canonicalHeader);
            return ret.orElse(url);
        }

        var responseUrl = new EdgeUrl(response.request().url().toString());
        if (!responseUrl.equals(url)) {
            return new EdgeUrl(response.request().url().toString());
        }

        return url;
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


    public static class BadContentType extends RuntimeException {
        public BadContentType(String type) {
            super(type);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
