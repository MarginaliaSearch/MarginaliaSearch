package nu.marginalia.wmsa.edge.crawler.domain;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.name.Named;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Singleton
public class DomainCrawlerRobotsTxt {

    private static final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();

    private final Cache<EdgeDomain, SimpleRobotRules> urlIdCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private final String userAgent;
    private final HttpFetcher fetcher;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainCrawlerRobotsTxt(HttpFetcher fetcher,
                                  @Named("user-agent-robots") String userAgent) {
        this.userAgent = userAgent;
        this.fetcher = fetcher;
    }

    @SneakyThrows
    public SimpleRobotRules fetchRulesCached(EdgeDomain domain) {
        return urlIdCache.get(domain, () -> fetchRulesRaw(domain));
    }

    private SimpleRobotRules fetchRulesRaw(EdgeDomain domain) {
        return fetchRobotsForProto("https", domain)
                .or(() -> fetchRobotsForProto("http", domain))
                .orElseGet(() -> new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL));
    }

    private Optional<SimpleRobotRules> fetchRobotsForProto(String proto, EdgeDomain domain) {
        try {
            var url = new EdgeUrl(proto, domain, null, "/robots.txt");
            return Optional.of(parseRobotsTxt(fetcher.fetchContent(url)));
        }
        catch (Exception ex) {
            return Optional.empty();
        }
    }

    private SimpleRobotRules parseRobotsTxt(EdgeRawPageContents edgePageContent) {
        return parser.parseContent(edgePageContent.url.toString(),
                edgePageContent.data.getBytes(StandardCharsets.UTF_8),
                edgePageContent.contentType.contentType,
                userAgent);
    }

}
