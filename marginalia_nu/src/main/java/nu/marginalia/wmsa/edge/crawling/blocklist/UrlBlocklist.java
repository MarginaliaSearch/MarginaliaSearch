package nu.marginalia.wmsa.edge.crawling.blocklist;

import nu.marginalia.util.gregex.GuardedRegexFactory;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class UrlBlocklist {
    private final List<Predicate<String>> patterns = new ArrayList<>();

    // domains that have a lot of links but we know we don't want to crawl
    private final Set<String> badDomains = Set.of("t.co", "facebook.com",
            "instagram.com", "youtube.com",
            "youtu.be", "amzn.to");

    public UrlBlocklist() {
        // Don't deep-crawl git repos
        patterns.add(s -> s.contains(".git/"));

        patterns.add(s -> s.contains("wp-content/upload"));
        patterns.add(s -> s.contains("-download-free"));

        // long base64-strings in URLs are typically git hashes or the like, rarely worth crawling
        patterns.add(GuardedRegexFactory.minLength(48, ".*/[^/]*[a-f0-9]{32,}(/|$)"));

        // link farms &c
        patterns.add(GuardedRegexFactory.contains("/download", "/download(-([A-Za-z]+|[0-9]+)){4,}\\.(htm|html|php)$"));
        patterns.add(GuardedRegexFactory.contains("/permalink", "/permalink/[a-z]+(-([A-Za-z]+|[0-9]+)){3,}\\.(htm|html|php)$"));
        patterns.add(GuardedRegexFactory.contains("webrx", "webrx3.*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$"));
        patterns.add(GuardedRegexFactory.contains("lib", "lib.*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$"));
        patterns.add(GuardedRegexFactory.contains("pdf", "pdf.*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$"));
        patterns.add(GuardedRegexFactory.contains("book", "book.*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$"));
        patterns.add(GuardedRegexFactory.contains("/720p", "720p.*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$"));
        patterns.add(GuardedRegexFactory.contains("/node","/node/.*/[a-z]+(-[a-z0-9]+)+.htm$"));

    }

    public boolean isUrlBlocked(EdgeUrl url) {
        try {
            if (badDomains.contains(url.domain.domain)) {
                return true;
            }

            if ("github.com".equals(url.domain.domain)) {
                return url.path.chars().filter(c -> c == '/').count() > 2;
            }

            for (var p : patterns) {
                if (p.test(url.path))
                    return true;
            }
        }
        catch (StackOverflowError ex) {
            return true;
        }
        return false;
    }

    public boolean isMailingListLink(EdgeUrl linkUrl) {
        var path = linkUrl.path;
        if (path.startsWith("/lists/")) {
            return true;
        }
        if (path.contains("mailinglist")) {
            return true;
        }
        return false;
    }
}
