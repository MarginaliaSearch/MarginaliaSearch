package nu.marginalia.converting.processor.pubdate.heuristic;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicGuessFromHtmlStandard implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, DocumentFormat htmlStandard) {
        if (htmlStandard == DocumentFormat.UNKNOWN)
            return Optional.empty();

        return Optional.of(new PubDate(null, PubDateParser.guessYear(htmlStandard)));
    }

}
