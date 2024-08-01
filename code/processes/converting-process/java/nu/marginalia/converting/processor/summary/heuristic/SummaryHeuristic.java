package nu.marginalia.converting.processor.summary.heuristic;

import org.jsoup.nodes.Document;

import java.util.Collection;

public interface SummaryHeuristic {
    String summarize(Document doc, Collection<String> importantWords);
}
