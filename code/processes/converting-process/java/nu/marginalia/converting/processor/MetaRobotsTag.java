package nu.marginalia.converting.processor;

import org.jsoup.nodes.Document;

import com.google.inject.Singleton;

@Singleton
public class MetaRobotsTag {
    private final String searchEngineName = "marginalia-search";

    public boolean allowIndexingByMetaTag(Document doc) {
        var robotsContent = doc.getElementsByTag("meta").select("meta[name=robots]").attr("content");

        if (isForbidden(robotsContent)) {
            var marginaliaTag = doc.select( "meta[name=" + searchEngineName + "]").attr("content");
            return isExplicitlyAllowed(marginaliaTag);
        }

        return true;
    }

    private boolean isForbidden(String robotsContent) {
        return robotsContent.contains("noindex") || robotsContent.contains("none");
    }

    private boolean isExplicitlyAllowed(String robotsContent) {
        return robotsContent.contains("all");
    }
}
