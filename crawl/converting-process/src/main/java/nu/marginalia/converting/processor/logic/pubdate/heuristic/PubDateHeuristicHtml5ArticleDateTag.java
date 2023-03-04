package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicHtml5ArticleDateTag implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        // HTML5
        for (var tag : document.select("time[pubdate=\"pubdate\"]")) {
            var maybeDate = PubDateParser.attemptParseDate(tag.attr("datetime"));
            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }

}
