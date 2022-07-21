package nu.marginalia.wmsa.edge.converting.processor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDomainStatus;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final Double minAvgDocumentQuality;

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           @Named("min-avg-document-quality") Double minAvgDocumentQuality
                           ) {
        this.documentProcessor = documentProcessor;
        this.minAvgDocumentQuality = minAvgDocumentQuality;
    }

    public ProcessedDomain process(CrawledDomain crawledDomain) {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain(crawledDomain.domain);
        ret.ip = crawledDomain.ip;

        if (crawledDomain.redirectDomain != null) {
            ret.redirect = new EdgeDomain(crawledDomain.redirectDomain);
        }

        if (crawledDomain.doc != null) {
            ret.documents = new ArrayList<>(crawledDomain.doc.size());

            for (var doc : crawledDomain.doc) {
                var processedDoc = documentProcessor.process(doc, crawledDomain);
                if (processedDoc.url != null) {
                    ret.documents.add(processedDoc);
                }
            }
        }
        else {
            ret.documents = Collections.emptyList();
        }

        double averageQuality = getAverageQuality(ret.documents);
        if (averageQuality < minAvgDocumentQuality) {
            ret.documents.forEach(doc -> doc.state = EdgeUrlState.DISQUALIFIED);
        }

        ret.state = getState(crawledDomain.crawlerStatus);

        return ret;
    }

    private double getAverageQuality(List<ProcessedDocument> documents) {
        int n = 0;
        double q = 0.;
        for (var doc : documents) {
            if (doc.quality().isPresent()) {
                n++;
                q += doc.quality().getAsDouble();
            }
        }

        if (n > 0) {
            return q / n;
        }
        return -5.;
    }

    private EdgeDomainIndexingState getState(String crawlerStatus) {
        return switch (CrawlerDomainStatus.valueOf(crawlerStatus)) {
            case OK -> EdgeDomainIndexingState.ACTIVE;
            case REDIRECT -> EdgeDomainIndexingState.REDIR;
            case BLOCKED -> EdgeDomainIndexingState.BLOCKED;
            default -> EdgeDomainIndexingState.ERROR;
        };
    }
}
