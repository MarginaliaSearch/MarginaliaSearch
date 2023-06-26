package nu.marginalia.crawl.retreival;

import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.*;
import java.util.function.Predicate;

public class DomainCrawlFrontier {
    private final LinkedList<EdgeUrl> queue = new LinkedList<>();
    private final HashSet<String> visited;
    private final HashSet<String> known;

    private final EdgeDomain thisDomain;
    private final UrlBlocklist urlBlocklist;

    private Predicate<EdgeUrl> linkFilter = url -> true;

    final int depth;

    public DomainCrawlFrontier(EdgeDomain thisDomain, Collection<String> urls, int depth) {
        this.thisDomain = thisDomain;
        this.urlBlocklist = new UrlBlocklist();
        this.depth = depth;

        visited = new HashSet<>((int)(urls.size() * 1.5));
        known = new HashSet<>(urls.size() * 10);

        for (String urlStr : urls) {
            EdgeUrl.parse(urlStr).ifPresent(this::addToQueue);
        }
    }

    public void setLinkFilter(Predicate<EdgeUrl> linkFilter) {
        this.linkFilter = linkFilter;
    }

    public boolean isCrawlDepthReached() {
        return visited.size() >= depth;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
    public boolean addKnown(EdgeUrl url) {
        return known.contains(url.toString());
    }
    public void addFirst(EdgeUrl url) {
        queue.addFirst(url);
    }

    public EdgeUrl takeNextUrl() {
        return queue.removeFirst();
    }

    public EdgeUrl peek() {
        return queue.peek();
    }

    public boolean addVisited(EdgeUrl url) {
        return visited.add(url.toString());
    }

    public boolean filterLink(EdgeUrl url) {
        return linkFilter.test(url);
    }

    public void addToQueue(EdgeUrl url) {
        if (!isSameDomain(url))
            return;
        if (urlBlocklist.isUrlBlocked(url))
            return;
        if (urlBlocklist.isMailingListLink(url))
            return;
        if (!linkFilter.test(url))
            return;

        // reduce memory usage by not growing queue huge when crawling large sites
        if (queue.size() + visited.size() >= depth + 100)
            return;

        if (known.add(url.toString())) {
            queue.addLast(url);
        }
    }

    public void addAllToQueue(Collection<EdgeUrl> urls) {
        for (var u : urls) {
            addToQueue(u);
        }
    }

    public boolean isSameDomain(EdgeUrl url) {
        return Objects.equals(thisDomain, url.domain);
    }

    public int queueSize() {
        return queue.size();
    }
}
