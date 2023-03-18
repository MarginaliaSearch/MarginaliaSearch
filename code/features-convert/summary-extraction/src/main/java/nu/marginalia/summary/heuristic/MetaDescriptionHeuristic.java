package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;

public class MetaDescriptionHeuristic implements SummaryHeuristic {
    @Override
    public String summarize(Document doc) {
        return doc.select("meta[name=description]").attr("content");
    }
}
