package nu.marginalia.converting.processor.summary.heuristic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.jsoup.nodes.Document;

import java.util.Collection;

public class DomFilterHeuristic implements SummaryHeuristic {
    private final int maxSummaryLength;

    @Inject
    public DomFilterHeuristic(@Named("max-summary-length") Integer maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    @Override
    public String summarize(Document doc, Collection<String> importantWords) {
        doc = doc.clone();

        var filter = new SummarizingDOMFilter();

        doc.filter(filter);

        return filter.getSummary(
                maxSummaryLength+32,
                    importantWords);
    }
}
