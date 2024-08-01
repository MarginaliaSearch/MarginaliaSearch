package nu.marginalia.crawl.retreival.fetcher;

import com.google.inject.ImplementedBy;
import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.retreival.RateLimitException;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;

import java.util.List;

@ImplementedBy(HttpFetcherImpl.class)
public interface HttpFetcher {
    void setAllowAllContentTypes(boolean allowAllContentTypes);

    List<String> getCookies();
    void clearCookies();

    FetchResult probeDomain(EdgeUrl url);

    HttpFetchResult fetchContent(EdgeUrl url, WarcRecorder recorder, ContentTags tags) throws RateLimitException;

    SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder);

    SitemapRetriever createSitemapRetriever();
}
