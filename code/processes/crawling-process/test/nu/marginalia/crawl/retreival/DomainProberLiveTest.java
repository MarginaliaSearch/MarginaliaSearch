package nu.marginalia.crawl.retreival;

import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.ip_blocklist.GeoIpBlocklist;
import nu.marginalia.ip_blocklist.IpBlockList;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class DomainProberLiveTest {

    @Test
    void probeLiveDomain() throws Exception {
        String domain = "www.marginalia.nu";

        EdgeUrl firstUrlInQueue = domain.startsWith("http")
                ? new EdgeUrl(domain)
                : new EdgeUrl("https://" + domain + "/");

        DomainProber prober = new DomainProber(new IpBlockList(new GeoIpBlocklist(new GeoIpDictionary())));

        try (HttpFetcher fetcher = new HttpFetcherImpl(WmsaHome.getUserAgent())) {
            HttpFetcher.DomainProbeResult result =
                    prober.probeDomain(fetcher, firstUrlInQueue.domain.toString(), firstUrlInQueue);

            System.out.println("Probed " + firstUrlInQueue + " as " + WmsaHome.getUserAgent().uaString());

            switch (result) {
                case HttpFetcher.DomainProbeResult.Ok(EdgeUrl probedUrl)
                        -> System.out.println("OK: crawl would proceed from " + probedUrl);
                case HttpFetcher.DomainProbeResult.Redirect(EdgeDomain redirectDomain)
                        -> System.out.println("REDIRECT: domain redirects to " + redirectDomain
                        + ", it would not be crawled under this name");
                case HttpFetcher.DomainProbeResult.RedirectSameDomain_Internal(EdgeUrl redirectUrl)
                        -> System.out.println("REDIRECT (same domain): " + redirectUrl);
                case HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus status, String desc)
                        -> System.out.println("ERROR (" + status + "): " + desc);
            }
        }
    }
}
