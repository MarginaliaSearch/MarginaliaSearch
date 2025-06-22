package nu.marginalia.ndp;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.WmsaHome;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.ndp.io.HttpClientProvider;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**  Evaluates a domain to determine if it is worth indexing.
 *  This class fetches the root document, checks the response code, content type,
 *  and parses the HTML to ensure it smells alright.
 */
@Singleton
public class DomainEvaluator {
    private final HttpClient client;
    private final String userAgentString = WmsaHome.getUserAgent().uaString();

    private final LinkParser linkParser = new LinkParser();
    private final DomainCoordinator domainCoordinator;

    @Inject
    public DomainEvaluator(DomainCoordinator domainCoordinator) throws NoSuchAlgorithmException, KeyManagementException {
        this.domainCoordinator = domainCoordinator;
        client = HttpClientProvider.createClient();
    }

    public boolean evaluateDomain(String domainName) {
        var edgeDomain = new EdgeDomain(domainName);

        // Grab a lock on the domain to prevent concurrent evaluations between processes
        try (var lock = domainCoordinator.lockDomain(edgeDomain)) {
            var rootUrl = edgeDomain.toRootUrlHttps();

            var request = ClassicRequestBuilder.get(rootUrl.asURI())
                    .addHeader("User-Agent", userAgentString)
                    .addHeader("Accept-Encoding", "gzip")
                    .addHeader("Accept", "text/html,application/xhtml+xml;q=0.9")
                    .build();

            return client.execute(request, (rsp) -> {
                if (rsp.getEntity() == null)
                    return false;

                try {
                    // Check if the response code indicates a successful fetch
                    if (200 != rsp.getCode()) {
                        return false;
                    }

                    byte[] content;
                    // Read the content from the response entity
                    try (InputStream contentStream = rsp.getEntity().getContent()) {
                        content = contentStream.readNBytes(8192);
                    }

                    // Parse the content (if it's valid)
                    ContentType contentType = ContentType.parse(rsp.getEntity().getContentType());

                    // Validate the content type
                    if (!contentType.contentType().startsWith("text/html") && !contentType.contentType().startsWith("application/xhtml+xml"))
                        return false;

                    // Parse the document body to a Jsoup Document
                    final Document document = Jsoup.parse(DocumentBodyToString.getStringData(contentType, content));
                    final String text = document.body().text();

                    if (text.length() < 100)
                        return false;
                    if (text.contains("404 Not Found") || text.contains("Page not found"))
                        return false;
                    if (hasMetaRefresh(document))
                        return false; // This almost always indicates a parked domain
                    if (!hasInternalLink(document, edgeDomain, rootUrl))
                        return false; // No internal links means it's not worth indexing

                    return true;
                }
                catch (Exception e) {
                    return false;
                }
                finally {
                    // May or may not be necessary, but let's ensure we clean up the response entity
                    // to avoid resource leaks
                    EntityUtils.consumeQuietly(rsp.getEntity());

                    // Sleep for a while before yielding the lock, to avoid immediately hammering the domain
                    // from another process
                    sleepQuietly(Duration.ofSeconds(1));
                }
            });
        }
        catch (Exception ex) {
            return false; // If we fail to fetch or parse the domain, we consider it invalid
        }
    }

    private boolean hasInternalLink(Document document, EdgeDomain currentDomain, EdgeUrl rootUrl) {
        for (Element atag : document.select("a")) {
            Optional<EdgeDomain> destDomain = linkParser
                    .parseLink(rootUrl, atag)
                    .map(EdgeUrl::getDomain);

            if (destDomain.isPresent() && Objects.equals(currentDomain, destDomain.get()))
                return true;
        }
        return false;
    }

    private boolean hasMetaRefresh(Document document) {
        for (Element metaTag : document.select("meta")) {
            if ("refresh".equalsIgnoreCase(metaTag.attr("http-equiv")))
                return true;
        }
        return false;
    }

    private void sleepQuietly(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
