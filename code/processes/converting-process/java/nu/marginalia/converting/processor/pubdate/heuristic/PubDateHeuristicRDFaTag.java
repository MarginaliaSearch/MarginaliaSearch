package nu.marginalia.converting.processor.pubdate.heuristic;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.model.DocumentTags;
import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicRDFaTag implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, DocumentTags tags, DocumentFormat htmlStandard) {
        for (var tag : tags.metaTags()) {
            if (!DocumentTags.attrIs(tag, "property", "datePublished"))
                continue;

            var maybeDate = PubDateParser.attemptParseDate(tag.attr("content"));
            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }

}
