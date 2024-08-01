package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class DefaultSpecialization implements HtmlProcessorSpecializations.HtmlProcessorSpecializationIf {

    private final SummaryExtractor summaryExtractor;

    @Inject
    public DefaultSpecialization(SummaryExtractor summaryExtractor) {
        this.summaryExtractor = summaryExtractor;
    }

    @Override
    public Document prune(Document doc) {
        final var prunedDoc = doc.clone();

        prunedDoc.getElementsByTag("svg").remove();
        prunedDoc.body().filter(new DomPruningFilter(0.5));

        return prunedDoc;
    }

    @Override
    public String getSummary(Document doc,
                             Set<String> importantWords) {
        List<String> cleanedWords = new ArrayList<>(importantWords.size());

        for (var word : importantWords) {
            // summary extraction is not interested in n-grams
            if (word.contains("_")) {
                continue;
            }

            cleanedWords.add(word);
        }

        return summaryExtractor.extractSummary(doc, cleanedWords);
    }
}
