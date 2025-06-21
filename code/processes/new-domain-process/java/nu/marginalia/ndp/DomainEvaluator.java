package nu.marginalia.ndp;


import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.ndp.io.HttpClientProvider;
import nu.marginalia.ndp.model.DomainToTest;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


public class DomainEvaluator {
    private final HttpClient client;
    private final String userAgentString = WmsaHome.getUserAgent().uaString();

    private final LinkParser linkParser = new LinkParser();
    private final DomainCoordinator domainCoordinator;
    sealed interface FetchResult permits FetchSuccess, FetchFailure {}
    record FetchSuccess(Document content) implements FetchResult {}
    record FetchFailure(String reason) implements FetchResult {}

    @Inject
    public DomainEvaluator(DomainCoordinator domainCoordinator) throws NoSuchAlgorithmException, KeyManagementException {
        this.domainCoordinator = domainCoordinator;
        client = HttpClientProvider.createClient();
    }

    public boolean evaluateDomain(DomainToTest domain) throws Exception {
        var edgeDomain = new EdgeDomain(domain.domainName());
        try (var lock = domainCoordinator.lockDomain(edgeDomain)) {
            var result = fetch(domain.domainName());

            Instant start = Instant.now();

            var ret = switch(result) {
                case FetchSuccess(Document content) -> validateHtml(content, edgeDomain);
                case FetchFailure failure -> false;
            };

            // Sleep for up to 1 second before we yield the lock to respect rate limits reasonably well
            Instant end = Instant.now();
            Duration sleepDuration = Duration.ofSeconds(1).minus(Duration.between(start, end));

            if (sleepDuration.isPositive()) {
                TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            }

            return ret;
        }
    }

    private boolean validateHtml(Document content, EdgeDomain domain) {
        var rootUrl = domain.toRootUrlHttps();
        var text = content.body().text();

        if (text.length() < 100) {
            return false; // Too short to be a valid page
        }

        if (text.contains("404 Not Found") || text.contains("Page not found")) {
            return false; // Common indicators of a 404 page
        }

        for (var metaTag : content.select("meta")) {
            if ("refresh".equalsIgnoreCase(metaTag.attr("http-equiv"))) {
                return false; // Page has a refresh tag, very likely a parked domain
            }
        }

        boolean hasInternalLink = false;

        for (var atag : content.select("a")) {
            var link = linkParser.parseLink(rootUrl, atag);
            if (link.isEmpty()) {
                continue; // Skip invalid links
            }
            var edgeUrl = link.get();
            if (Objects.equals(domain, edgeUrl.getDomain())) {
                hasInternalLink = true;
            }
        }

        return hasInternalLink;
    }

    private FetchResult fetch(String domain) throws URISyntaxException {
        var uri = new URI("https://" + domain + "/");

        var request = ClassicRequestBuilder.get(uri)
                .addHeader("User-Agent", userAgentString)
                .addHeader("Accept-Encoding", "gzip")
                .addHeader("Accept", "text/html,application/xhtml+xml;q=0.9")
                .build();

        try {
            return client.execute(request, (rsp) -> responseHandler(rsp, domain));
        } catch (Exception e) {
            return new FetchFailure("Failed to fetch domain: " + e.getMessage());
        }
    }

    private FetchResult responseHandler(ClassicHttpResponse rsp, String domain) {
        if (rsp.getEntity() == null)
            return new FetchFailure("No content returned from " + domain);

        try {
            int code = rsp.getCode();
            byte[] content = rsp.getEntity().getContent().readAllBytes();

            if (code >= 300) {
                return new FetchFailure("Received HTTP " + code + " from " + domain);
            }

            ContentType contentType = ContentType.parse(rsp.getEntity().getContentType());
            var html = DocumentBodyToString.getStringData(contentType, content);
            return new FetchSuccess(Jsoup.parse(html));
        }
        catch (Exception e) {
            EntityUtils.consumeQuietly(rsp.getEntity());
            return new FetchFailure("Failed to read content from " + domain + ": " + e.getMessage());
        }
    }

}
