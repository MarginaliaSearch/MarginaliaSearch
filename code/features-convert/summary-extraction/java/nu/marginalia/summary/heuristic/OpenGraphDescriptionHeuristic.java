package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;

import java.util.Collection;

public class OpenGraphDescriptionHeuristic implements SummaryHeuristic {
    @Override
    public String summarize(Document doc, Collection<String> importantWords) {
        return doc.select("meta[name=og:description]").attr("content");
    }
}
