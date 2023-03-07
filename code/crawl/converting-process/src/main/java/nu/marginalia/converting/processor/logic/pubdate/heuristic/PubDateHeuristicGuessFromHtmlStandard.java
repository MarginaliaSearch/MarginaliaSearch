package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicGuessFromHtmlStandard implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        if (htmlStandard == EdgeHtmlStandard.UNKNOWN)
            return Optional.empty();

        return Optional.of(new PubDate(null, PubDateParser.guessYear(htmlStandard)));
    }

}
