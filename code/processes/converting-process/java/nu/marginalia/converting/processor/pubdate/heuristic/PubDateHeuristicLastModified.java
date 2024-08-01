package nu.marginalia.converting.processor.pubdate.heuristic;

import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.html.HtmlStandard;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicLastModified implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
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
