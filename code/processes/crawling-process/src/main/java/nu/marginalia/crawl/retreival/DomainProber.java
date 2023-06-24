package nu.marginalia.crawl.retreival;

import nu.marginalia.crawl.retreival.fetcher.FetchResultState;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawling.model.CrawlerDomainStatus;
import nu.marginalia.ip_blocklist.GeoIpBlocklist;
import nu.marginalia.ip_blocklist.IpBlockList;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class DomainProber {
    private final Logger logger = LoggerFactory.getLogger(DomainProber.class);
    private static IpBlockList ipBlockList;

    static {
        try {
            ipBlockList = new IpBlockList(new GeoIpBlocklist());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** To detect problems early we do a probing request to the domain before we start crawling it properly.
     *  This is a HEAD, typically to the root path.  We check the IP against the blocklist, we check that it
     *  doesn't immediately redirect to another domain (which should be crawled separately, not under the name
     *  of this domain).
     */
    public ProbeResult probeDomain(HttpFetcher fetcher, String domain, @Nullable EdgeUrl firstUrlInQueue) {

        if (firstUrlInQueue == null) {
            logger.warn("No valid URLs for domain {}", domain);

            return new ProbeResultError(CrawlerDomainStatus.ERROR, "No known URLs");
        }

        if (!ipBlockList.isAllowed(firstUrlInQueue.domain))
            return new ProbeResultError(CrawlerDomainStatus.BLOCKED, "IP not allowed");

        var fetchResult = fetcher.probeDomain(firstUrlInQueue.withPathAndParam("/", null));

        if (fetchResult.ok())
            return new ProbeResultOk();

        if (fetchResult.state == FetchResultState.REDIRECT)
            return new ProbeResultRedirect(fetchResult.domain);

        return new ProbeResultError(CrawlerDomainStatus.ERROR, "Bad status");
    }

    interface ProbeResult {};

    record ProbeResultError(CrawlerDomainStatus status, String desc) implements ProbeResult {}
    record ProbeResultRedirect(EdgeDomain domain) implements ProbeResult {}
    record ProbeResultOk() implements ProbeResult {}
}
