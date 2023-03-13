package nu.marginalia.crawl.retreival;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.client.exception.NetworkException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

// TODO: Is this used?
@Singleton
public class HttpRedirectResolver {
    private static final LinkParser linkParser = new LinkParser();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgent;
    private final Cookies cookies = new Cookies();

    private final OkHttpClient client = createClient();

    @SneakyThrows
    private OkHttpClient createClient() {

        return new OkHttpClient.Builder()
            .sslSocketFactory(NoSecuritySSL.buildSocketFactory(), (X509TrustManager) NoSecuritySSL.trustAllCerts[0])
            .hostnameVerifier(NoSecuritySSL.buildHostnameVerifyer())
            .cookieJar(cookies.getJar())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build();
    }

    @Inject
    public HttpRedirectResolver(@Named("user-agent") String userAgent) {
        this.userAgent = userAgent;
    }

    @SneakyThrows
    public Observable<EdgeUrl> probe(EdgeUrl url) {
        return probe(url, 0);
    }

    private Observable<EdgeUrl> probe(EdgeUrl url, int depth) {
        if (depth > 10) {
            return Observable.error(new IllegalStateException("Too many redirects"));
        }
        if (!url.proto.toLowerCase().startsWith("http")) {
            return Observable.empty();
        }
        var head = new Request.Builder().get().addHeader("User-agent", userAgent)
                .url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .build();

        var call = client.newCall(head);
        try (var rsp = call.execute()) {
            return resolveRedirects(depth, url, rsp);
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    @SneakyThrows
    private Observable<EdgeUrl> resolveRedirects(int depth, EdgeUrl url, Response response) {
        int code = response.code();
        response.close();

        if (code < 300) {
            return Observable.just(url);
        }
        if (code < 309) {
            String newUrl = response.header("Location");
            return Observable.fromOptional(linkParser.parseLink(url, newUrl))
                    .flatMap(u -> probe(u, depth + 1));
        }
        if (code >= 400) {
            return Observable.just(url);
        }
        return Observable.error(new IllegalStateException("HttpStatusCode " + code));
    }


    private boolean failOnBadStatus(Response response) {
        if (response.code() >= 400) {
            response.close();
            throw new NetworkException("Bad status " + response.code());
        }
        return true;
    }

    public static class BadContentType extends RuntimeException {
        public BadContentType(String type) {
            super(type);
        }
    }
}
