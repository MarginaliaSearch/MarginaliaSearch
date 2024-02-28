package nu.marginalia.crawl.retreival;

import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.function.Predicate;

public class LinkFilterSelector {

    /* With websites that run e.g. forum software or wiki software, it's
       very beneficial to cherry-pick the URLs that we want to crawl to
       exclude e.g. user profiles, and other similar noise.
     */
    public Predicate<EdgeUrl> selectFilter(Document doc) {

        var head = doc.getElementsByTag("head").first();
        if (null == head) {
            return url -> true;
        }

        if (isLemmy(head)) {
            return url -> url.path.startsWith("/post/")
               || (url.path.startsWith("/c/") && !url.path.contains("@"));
        }
        if (isDiscourse(head)) {
            return url -> url.path.startsWith("/t/") || url.path.contains("/latest");
        }

        return LinkFilterSelector::defaultFilter;
    }

    public static boolean defaultFilter(EdgeUrl url) {
        return true;
    }

    private boolean isMediawiki(Element head) {
        return head.select("meta[name=generator]").attr("content").toLowerCase().contains("mediawiki");
    }
    private boolean isDiscourse(Element head) {
        return head.select("meta[name=generator]").attr("content").toLowerCase().contains("discourse");
    }
    private boolean isLemmy(Element head) {
        for (var scriptTags : head.select("script")) {
            if (scriptTags.html().contains("window.lemmyConfig")) {
                return true;
            }
        }
        return false;
    }
}
