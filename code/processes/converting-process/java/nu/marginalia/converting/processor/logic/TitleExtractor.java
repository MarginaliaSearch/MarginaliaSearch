package nu.marginalia.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.language.model.DocumentLanguageData;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

public class TitleExtractor {

    private final int maxTitleLength;

    @Inject
    public TitleExtractor(@Named("max-title-length") Integer maxTitleLength) {
        this.maxTitleLength = maxTitleLength;
    }

    public String getTitleAbbreviated(Document doc, DocumentLanguageData dld, String url) {
        return StringUtils.abbreviate(getFullTitle(doc, dld, url), maxTitleLength);
    }
    public String getFullTitle(Document doc, DocumentLanguageData dld, String url) {
        String title;

        title = getFirstTagText(doc, "head > title");
        if (title != null) return title;

        title = getFirstTagText(doc, "h1");
        if (title != null) return title;

        title = getFirstTagText(doc, "h2");
        if (title != null) return title;

        title = getFirstTagText(doc, "h3");
        if (title != null) return title;

        title = getFirstTagText(doc, "h4");
        if (title != null) return title;

        title = getFirstTagText(doc, "h5");
        if (title != null) return title;

        if (dld.sentences.length > 0) {
            return dld.sentences[0].originalSentence;
        }

        return url;
    }

    private String getFirstTagText(Document doc, String selector) {
        var firstTag = doc.selectFirst(selector);
        if (firstTag != null) {
            return firstTag.text();
        }
        return null;
    }
}
