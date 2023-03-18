package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;

public interface SummaryHeuristic {
    String summarize(Document doc);
}
