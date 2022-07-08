package nu.marginalia.wmsa.edge.crawling.blocklist;

import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class UrlBlocklist {
    private final List<Predicate<String>> patterns = new ArrayList<>();

    public UrlBlocklist() {
        patterns.add(Pattern.compile(".*/[a-f0-9]{40}(/|$)").asPredicate());
        patterns.add(Pattern.compile("/download(-([A-Za-z]+|[0-9]+)){4,}\\.(htm|html|php)$").asPredicate());
        patterns.add(Pattern.compile("/download(-([A-Za-z]+|[0-9]+)){4,}\\.(htm|html|php)$").asPredicate());
        patterns.add(Pattern.compile("/permalink/[a-z]+(-([A-Za-z]+|[0-9]+)){3,}\\.(htm|html|php)$").asPredicate());
        patterns.add(Pattern.compile("(webrx3|lib|pdf|book|720p).*/[A-Za-z]+(-([A-Za-z]+|[0-9]+)){3,}((-[0-9]+)?/|\\.(php|htm|html))$").asPredicate());
        patterns.add(Pattern.compile("/node/.*/[a-z]+(-[a-z0-9]+)+.htm$").asPredicate());
        patterns.add(Pattern.compile(".*-download-free$").asPredicate());
    }

    public boolean isUrlBlocked(EdgeUrl url) {
        try {
            if ("github.com".equals(url.domain.domain)) {
                return url.path.chars().filter(c -> c == '/').count() > 2;
            }

            return patterns.stream().anyMatch(p -> p.test(url.path));
        }
        catch (StackOverflowError ex) {
            return true;
        }
    }

    public boolean isMailingListLink(EdgeUrl linkUrl) {
        var path = linkUrl.path;
        if (path.startsWith("/lists/")) {
            return true;
        }
        if (path.startsWith("mailinglist")) {
            return true;
        }
        return false;
    }



}
