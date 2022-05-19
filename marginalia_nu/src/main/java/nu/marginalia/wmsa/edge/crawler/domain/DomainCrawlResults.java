package nu.marginalia.wmsa.edge.crawler.domain;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageContent;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor @ToString
public class DomainCrawlResults {
    public final EdgeDomain domain;
    public final double rank;
    public final int pass;

    public final long crawlStart = System.currentTimeMillis();

    public final Set<EdgeUrl> extUrl = new HashSet<>();
    public final Set<EdgeUrl> intUrl = new HashSet<>();
    public final Set<EdgeUrl> visitedUrl = new HashSet<>();
    public final Set<EdgeUrl> feeds = new HashSet<>();
    public final Map<EdgeUrl, EdgeUrlState> urlStates = new HashMap<>();

    public final Map<EdgeUrl, EdgePageContent> pageContents = new HashMap<>();
    public final HashSet<EdgeDomainLink> links = new HashSet<>();

    public final List<EdgeUrlVisit> visits() {
        return visitedUrl.stream().map(url -> {
            var page = pageContents.get(url);
            if (page != null) {
                return new EdgeUrlVisit(url,
                        page.hash,
                        page.getMetadata().quality(),
                        page.metadata.title.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", ""),
                        page.metadata.description.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", ""),
                        page.ipAddress,
                        page.metadata.htmlStandard.toString(),
                        page.metadata.features,
                        page.metadata.textDistinctWords,
                        page.metadata.totalWords,
                        urlStates.getOrDefault(url, EdgeUrlState.OK)
                        );
            }
            else {
                return new EdgeUrlVisit(url, null, null, null, null,null, "text", 0, 0, 0,
                        urlStates.getOrDefault(url, EdgeUrlState.OK));
            }
        }).collect(Collectors.toList());
    }

}
