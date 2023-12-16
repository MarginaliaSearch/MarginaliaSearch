package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;

public class DomainCrawlFrontier {

    private static final LinkParser linkParser = new LinkParser();

    private final ArrayDeque<String> queue;

    // To save the number of strings kept in memory,
    // do an approximate check using 64 bit hashes instead
    // ..
    // This isn't perfect, and may lead to false positives,
    // but this is relatively unlikely, since the cardinality of these
    // need to be in the billions to approach Birthday Paradox
    // territory
    private final LongOpenHashSet visited;
    private final LongOpenHashSet known;
    private final HashFunction hasher = com.google.common.hash.Hashing.murmur3_128();

    private final EdgeDomain thisDomain;
    private final UrlBlocklist urlBlocklist;

    private Predicate<EdgeUrl> linkFilter = url -> true;

    private int depth;

    public DomainCrawlFrontier(EdgeDomain thisDomain, Collection<String> urls, int depth) {
        this.thisDomain = thisDomain;
        this.urlBlocklist = new UrlBlocklist();
        this.depth = depth;

        queue = new ArrayDeque<>(10 + (int) (urls.size()*1.2));
        visited = new LongOpenHashSet(10 + (int)(urls.size() * 1.5));
        known = new LongOpenHashSet(10 + urls.size() * 2);

        for (String urlStr : urls) {
            EdgeUrl.parse(urlStr).ifPresent(this::addToQueue);
        }
    }

    /** Increase the depth of the crawl by a factor.  If the current depth is smaller
     * than the number of already visited documents, the base depth will be adjusted
     * to the visited count first.
     */
    public void increaseDepth(double depthIncreaseFactor) {
        depth = (int)(Math.max(visited.size(), depth) * depthIncreaseFactor);
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

    public void addFirst(EdgeUrl url) {
        if (addKnown(url)) {
            queue.addFirst(url.toString());
        }
    }

    public EdgeUrl takeNextUrl() {
        try {
            return new EdgeUrl(queue.removeFirst());
        } catch (URISyntaxException e) {
            // This should never happen since we only add urls via EdgeUrl.toString()
            throw new RuntimeException(e);
        }
    }

    public EdgeUrl peek() {
        try {
            if (queue.peek() == null) {
                return null;
            }
            return new EdgeUrl(queue.peek());
        } catch (URISyntaxException e) {
            // This should never happen since we only add urls via EdgeUrl.toString()
            throw new RuntimeException(e);
        }
    }

    public boolean addVisited(EdgeUrl url) {
        long hashCode = hasher.hashUnencodedChars(url.toString()).padToLong();

        return visited.add(hashCode);
    }
    public boolean addKnown(EdgeUrl url) {
        long hashCode = hasher.hashUnencodedChars(url.toString()).padToLong();
        return known.add(hashCode);
    }

    boolean isVisited(EdgeUrl url) {
        long hashCode = hasher.hashUnencodedChars(url.toString()).padToLong();
        return visited.contains(hashCode);
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
        if (queue.size() + visited.size() >= depth + 200)
            return;

        if (isVisited(url))
            return;

        if (addKnown(url)) {
            queue.addLast(url.toString());
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


    public void enqueueLinksFromDocument(EdgeUrl baseUrl, Document parsed) {
        baseUrl = linkParser.getBaseLink(parsed, baseUrl);

        for (var link : parsed.getElementsByTag("a")) {
            linkParser.parseLink(baseUrl, link).ifPresent(this::addToQueue);
        }
        for (var link : parsed.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(this::addToQueue);
        }
        for (var link : parsed.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(this::addToQueue);
        }
        for (var link : parsed.getElementsByTag("link")) {
            String rel = link.attr("rel");

            if (rel.equalsIgnoreCase("next") || rel.equalsIgnoreCase("prev")) {
                linkParser.parseLink(baseUrl, link).ifPresent(this::addToQueue);
            }
        }
    }

}
