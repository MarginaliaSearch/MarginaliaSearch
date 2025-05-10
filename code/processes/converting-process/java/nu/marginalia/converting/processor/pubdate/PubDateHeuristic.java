package nu.marginalia.converting.processor.pubdate;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface PubDateHeuristic {

    Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, DocumentFormat htmlStandard);
}
