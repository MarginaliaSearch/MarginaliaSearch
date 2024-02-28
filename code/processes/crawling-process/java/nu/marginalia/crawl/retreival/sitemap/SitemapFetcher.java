package nu.marginalia.crawl.retreival.sitemap;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.retreival.DomainCrawlFrontier;
import nu.marginalia.crawl.retreival.fetcher.SitemapRetriever;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SitemapFetcher {

    private final DomainCrawlFrontier crawlFrontier;
    private final SitemapRetriever sitemapRetriever;
    private static final Logger logger = LoggerFactory.getLogger(SitemapFetcher.class);

    public SitemapFetcher(DomainCrawlFrontier crawlFrontier, SitemapRetriever sitemapRetriever) {
        this.crawlFrontier = crawlFrontier;
        this.sitemapRetriever = sitemapRetriever;
    }

    public void downloadSitemaps(SimpleRobotRules robotsRules, EdgeUrl rootUrl) {
        List<String> sitemaps = robotsRules.getSitemaps();

        List<EdgeUrl> urls = new ArrayList<>(sitemaps.size());
        if (!sitemaps.isEmpty()) {
            for (var url : sitemaps) {
                EdgeUrl.parse(url).ifPresent(urls::add);
            }
        }
        else {
            urls.add(rootUrl.withPathAndParam("/sitemap.xml", null));
        }

        downloadSitemaps(urls);
    }

    public void downloadSitemaps(List<EdgeUrl> urls) {

        Set<String> checkedSitemaps = new HashSet<>();

        for (var url : urls) {
            // Let's not download sitemaps from other domains for now
            if (!crawlFrontier.isSameDomain(url)) {
                continue;
            }

            if (checkedSitemaps.contains(url.path))
                continue;

            var sitemap =  sitemapRetriever.fetchSitemap(url);
            if (sitemap.isEmpty()) {
                continue;
            }

            // ensure we don't try to download this sitemap again
            // (don't move this up, as we may want to check the same
            // path with different protocols until we find one that works)

            checkedSitemaps.add(url.path);

            crawlFrontier.addAllToQueue(sitemap);
        }

        logger.debug("Queue is now {}", crawlFrontier.queueSize());
    }
}
