package nu.marginalia.crawl.fetcher;

import com.google.inject.ImplementedBy;
import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;

import java.util.List;

@ImplementedBy(HttpFetcherImpl.class)
public interface HttpFetcher {
    void setAllowAllContentTypes(boolean allowAllContentTypes);

    List<String> getCookies();
    void clearCookies();

    HttpFetcherImpl.ProbeResult probeDomain(EdgeUrl url);

    HttpFetchResult fetchContent(EdgeUrl url,
                                 WarcRecorder recorder,
                                 ContentTags tags,
                                 ProbeType probeType) throws HttpFetcherImpl.RateLimitException;

    SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder);

    SitemapRetriever createSitemapRetriever();

    enum ProbeType {
        DISABLED,
        FULL,
        IF_MODIFIED_SINCE
    }
}
