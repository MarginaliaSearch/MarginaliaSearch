package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;

import java.util.Collection;

public class MetaDescriptionHeuristic implements SummaryHeuristic {
    @Override
    public String summarize(Document doc, Collection<String> importantWords) {
        return doc.select("meta[name=description]").attr("content");
    }
}
