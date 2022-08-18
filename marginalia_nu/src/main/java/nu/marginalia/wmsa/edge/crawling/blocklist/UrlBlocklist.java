package nu.marginalia.wmsa.edge.crawling.blocklist;

import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class UrlBlocklist {
    private final List<Predicate<String>> patterns = new ArrayList<>();

    // domains that have a lot of links but we know we don't want to crawl
    private final Set<String> badDomains = Set.of("t.co", "facebook.com",
            "instagram.com", "youtube.com",
            "youtu.be", "amzn.to");

    public UrlBlocklist() {
        // Don't deep-crawl git repos
        patterns.add(Pattern.compile("\\.git/.+").asPredicate());
        patterns.add(Pattern.compile("wp-content/upload").asPredicate());

        // long base64-strings in URLs are typically git hashes or the like, rarely worth crawling
        patterns.add(Pattern.compile(".*/[^/]*[a-f0-9]{32,}(/|$)").asPredicate());

        // link farms &c
        patterns.add(Pattern.compile("/download(-([A-Za-z]+|[0-9]+)){4,}\\.(htm|html|php)$").asPredicate());
        patterns.add(Pattern.compile("/permalink/[a-z]+(-([A-Za-z]+|[0-9]+)){3,}\\.(htm|html|php)$").asPredicate());
        patterns.add(Pattern.compile("(webrx3|lib|pdf|book|720p).*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$").asPredicate());
        patterns.add(Pattern.compile("/node/.*/[a-z]+(-[a-z0-9]+)+.htm$").asPredicate());
        patterns.add(Pattern.compile(".*-download-free$").asPredicate());
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
