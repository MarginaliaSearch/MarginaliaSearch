package nu.marginalia.converting.processor;

import nu.marginalia.converting.model.DocumentTags;

import com.google.inject.Singleton;

@Singleton
public class MetaRobotsTag {
    private final String searchEngineName = "marginalia-search";

    public boolean allowIndexingByMetaTag(DocumentTags tags) {
        var robotsContent = metaContent(tags, "robots");

        if (isForbidden(robotsContent)) {
            var marginaliaTag = metaContent(tags, searchEngineName);
            return isExplicitlyAllowed(marginaliaTag);
        }

        return true;
    }

    private String metaContent(DocumentTags tags, String name) {
        for (var meta : tags.metaTags()) {
            if (DocumentTags.attrIs(meta, "name", name) && meta.hasAttr("content")) {
                return meta.attr("content");
            }
        }
        return "";
    }

    private boolean isForbidden(String robotsContent) {
        return robotsContent.contains("noindex") || robotsContent.contains("none");
    }

    private boolean isExplicitlyAllowed(String robotsContent) {
        return robotsContent.contains("all");
    }
}
