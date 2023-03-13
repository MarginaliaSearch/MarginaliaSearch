package nu.marginalia.pubdate.heuristic;

import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.pubdate.PubDateEffortLevel;
import nu.marginalia.pubdate.PubDateHeuristic;
import nu.marginalia.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicOpenGraph implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
        // OG
        for (var tag : document.select("meta[property=\"article:published_time\"]")) {
            var maybeDate = PubDateParser.attemptParseDate(tag.attr("content"));
            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }
}
