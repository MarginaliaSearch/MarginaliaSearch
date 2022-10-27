package nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDate;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateEffortLevel;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicLastModified implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        String lmString = "last-modified: ";
        int offset = headers.toLowerCase().indexOf(lmString);

        if (offset < 0)
            return Optional.empty();
        int end = headers.indexOf('\n', offset);
        if (end < 0) end = headers.length();

        String lmDate = headers.substring(offset + lmString.length(), end);
        return PubDateParser.attemptParseDate(lmDate);
    }

}
