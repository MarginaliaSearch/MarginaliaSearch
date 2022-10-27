package nu.marginalia.wmsa.edge.converting.processor.logic.pubdate;

import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface PubDateHeuristic {

    Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard);
}
