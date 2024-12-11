package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** This class is used to specify how to process a website running Lemmy */
@Singleton
public class LemmySpecialization extends DefaultSpecialization {
    private static final Logger logger = LoggerFactory.getLogger(LemmySpecialization.class);
    private final SummaryExtractor summaryExtractor;

    @Inject
    public LemmySpecialization(SummaryExtractor summaryExtractor, TitleExtractor titleExtractor) {
        super(summaryExtractor, titleExtractor);
        this.summaryExtractor = summaryExtractor;
    }

    public Document prune(Document document) {

        // Remove the sidebar

        var newDoc = new Document(document.baseUri());
        var bodyTag = newDoc.appendElement("body");

        for (var pTag : document.getElementsByTag("p")) {
            bodyTag.appendChild(newDoc.createElement("p").text(pTag.text()));
        }

        return newDoc;
    }

    public String getSummary(Document document, Set<String> importantWords) {
        StringBuilder summary = new StringBuilder();

        for (var pTag : document.getElementsByTag("p")) {
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

    /** Since we're stripping down the document to only contain the relevant comments,
     * we need to add an artificial lenght modifier to the document to avoid filtering out
     * documents that are of adequate length but fail to meet the minimum length requirement
     * that assumes a certain amount of chaff.
     */
    @Override
    public double lengthModifier() {
        return 1.5;
    }
}
