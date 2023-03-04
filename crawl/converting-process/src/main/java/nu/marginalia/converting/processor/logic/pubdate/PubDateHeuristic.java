package nu.marginalia.converting.processor.logic.pubdate;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface PubDateHeuristic {

    Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard);
}
