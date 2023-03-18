package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;

public class OpenGraphDescriptionHeuristic implements SummaryHeuristic {
    @Override
    public String summarize(Document doc) {
        return doc.select("meta[name=og:description]").attr("content");
    }
}
