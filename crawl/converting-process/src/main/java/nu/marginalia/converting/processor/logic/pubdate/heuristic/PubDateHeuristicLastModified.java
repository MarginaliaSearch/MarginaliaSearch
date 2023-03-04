package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
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
