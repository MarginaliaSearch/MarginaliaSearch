package nu.marginalia.pubdate;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.crawling.common.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface PubDateHeuristic {

    Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard);
}
