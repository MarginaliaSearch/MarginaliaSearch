package nu.marginalia.crawl.fetcher;

import com.google.inject.ImplementedBy;
import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.apache.hc.client5.http.cookie.CookieStore;

import java.util.List;

@ImplementedBy(HttpFetcherImpl.class)
public interface HttpFetcher extends AutoCloseable {
    void setAllowAllContentTypes(boolean allowAllContentTypes);

    CookieStore getCookies();
    void clearCookies();

    DomainProbeResult probeDomain(EdgeUrl url);

    HttpFetchResult fetchContent(EdgeUrl url,
                                 WarcRecorder recorder,
                                 CrawlDelayTimer timer,
                                 ContentTags tags,
                                 ProbeType probeType);

    List<EdgeUrl> fetchSitemapUrls(String rootSitemapUrl, CrawlDelayTimer delayTimer);

    SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder);

    SitemapRetriever createSitemapRetriever();

    enum ProbeType {
        DISABLED,
        FULL,
    }

    sealed interface DomainProbeResult {
        record Error(CrawlerDomainStatus status, String desc) implements DomainProbeResult {}

        /** This domain redirects to another domain */
        record Redirect(EdgeDomain domain) implements DomainProbeResult {}
        record RedirectSameDomain_Internal(EdgeUrl domain) implements DomainProbeResult {}

        /** If the retrieval of the probed url was successful, return the url as it was fetched
         * (which may be different from the url we probed, if we attempted another URL schema).
         *
         * @param probedUrl  The url we successfully probed
         */
        record Ok(EdgeUrl probedUrl) implements DomainProbeResult {}
    }

    sealed interface ContentTypeProbeResult {
        record Ok(EdgeUrl resolvedUrl) implements ContentTypeProbeResult { }
        record HttpError(int statusCode, String message) implements ContentTypeProbeResult { }
        record Redirect(EdgeUrl location) implements ContentTypeProbeResult { }
        record BadContentType(String contentType, int statusCode) implements ContentTypeProbeResult { }
        record Timeout(java.lang.Exception ex) implements ContentTypeProbeResult { }
        record Exception(java.lang.Exception ex) implements ContentTypeProbeResult { }
    }
}
