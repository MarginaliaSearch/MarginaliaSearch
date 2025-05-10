package nu.marginalia.converting.processor.pubdate.heuristic;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Optional;

public class PubDateHeuristicLastModified implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, DocumentFormat htmlStandard) {
        List<String> lastModified = headers.get("last-modified");
        if (lastModified.isEmpty())
            return Optional.empty();
        return PubDateParser.attemptParseDate(lastModified.getFirst());
    }

}
