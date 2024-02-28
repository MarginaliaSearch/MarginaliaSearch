package nu.marginalia.pubdate.heuristic;

import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.pubdate.PubDateEffortLevel;
import nu.marginalia.pubdate.PubDateHeuristic;
import nu.marginalia.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicRDFaTag implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
        for (var tag : document.select("meta[property=\"datePublished\"]")) {
            var maybeDate = PubDateParser.attemptParseDate(tag.attr("content"));
            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }

}
