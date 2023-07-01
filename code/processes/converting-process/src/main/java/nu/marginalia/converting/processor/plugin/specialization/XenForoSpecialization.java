package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.summary.SummaryExtractor;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Singleton
public class XenForoSpecialization implements HtmlProcessorSpecializations.HtmlProcessorSpecializationIf {
    private static final Logger logger = LoggerFactory.getLogger(XenForoSpecialization.class);
    private final SummaryExtractor summaryExtractor;

    @Inject
    public XenForoSpecialization(SummaryExtractor summaryExtractor) {
        this.summaryExtractor = summaryExtractor;
    }

    public Document prune(Document document) {

        // Remove the sidebar

        var newDoc = new Document(document.baseUri());
        var bodyTag = newDoc.appendElement("body");
        var article = bodyTag.appendElement("article");
        var firstTime = document.getElementsByTag("time").first();

        if (firstTime != null) {
            // Ensure we get the publish date
            var timeTag = newDoc.createElement("time");

            timeTag.attr("datetime", firstTime.attr("datetime"));
            timeTag.attr("pubdate", "pubdate");
            timeTag.text(firstTime.attr("datetime"));

            article.appendChild(timeTag);
        }

        for (var post : document.getElementsByClass("message-inner")) {
            String user = post.getElementsByClass("message-name").text();
            String text = post.getElementsByClass("bbWrapper").text();
            article.appendChild(newDoc.createElement("p").text(user + ": " + text));
        }

        return newDoc;
    }

    public String getSummary(Document document, Set<String> importantWords) {
        StringBuilder summary = new StringBuilder();

        for (var pTag : document.getElementsByClass("bbWrapper")) {
            if (summary.length() > 512) {
                break;
            }
            String text = pTag.text();

            if (text.isBlank())
                continue;

            summary
                    .append(text)
                    .append(' ');
        }

        return summaryExtractor.abbreivateSummary(summary.toString());
    }

    @Override
    public double lengthModifier() {
        return 1.25;
    }
}
