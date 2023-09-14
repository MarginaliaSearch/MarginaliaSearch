package nu.marginalia.pubdate.heuristic;

import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.pubdate.PubDateEffortLevel;
import nu.marginalia.pubdate.PubDateHeuristic;
import nu.marginalia.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicGuessFromHtmlStandard implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
        if (htmlStandard == HtmlStandard.UNKNOWN)
            return Optional.empty();

        return Optional.of(new PubDate(null, PubDateParser.guessYear(htmlStandard)));
    }

}
