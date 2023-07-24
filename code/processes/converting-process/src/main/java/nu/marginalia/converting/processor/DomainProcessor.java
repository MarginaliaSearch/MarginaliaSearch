package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.crawling.model.*;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.links.TopKeywords;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;
import nu.marginalia.model.crawl.HtmlFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final LshDocumentDeduplicator documentDeduplicator;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           LshDocumentDeduplicator documentDeduplicator) {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.documentDeduplicator = documentDeduplicator;
    }

    public ProcessedDomain process(Iterator<SerializableCrawlData> dataStream) {
        var ret = new ProcessedDomain();
        List<ProcessedDocument> docs = new ArrayList<>();

        boolean cookies = false;
        String ip = "";
        while (dataStream.hasNext()) {
            var data = dataStream.next();

            if (data instanceof CrawledDomain crawledDomain) {
                ret.domain = new EdgeDomain(crawledDomain.domain);
                ret.ip = crawledDomain.ip;
                ret.id = crawledDomain.id;

                cookies = Objects.requireNonNullElse(crawledDomain.cookies, Collections.emptyList()).size() > 0;
                ip = crawledDomain.ip;

                if (crawledDomain.redirectDomain != null) {
                    ret.redirect = new EdgeDomain(crawledDomain.redirectDomain);
                }
                ret.documents = docs;
                ret.state = getState(crawledDomain.crawlerStatus);
            }
            else if (data instanceof CrawledDocument doc) {
                try {
                    if (doc.url == null)
                        continue;
                    fixBadCanonicalTag(doc);

                    docs.add(documentProcessor.process(doc));
                }
                catch (Exception ex) {
                    logger.warn("Failed to process " + doc.url, ex);
                }
            }
        }

        // Add late keywords and features from domain-level information

        List<String> terms = new ArrayList<>();
        terms.add("ip:"+ip);
        if (cookies)
            terms.add(HtmlFeature.COOKIES.getKeyword());

        for (var document : ret.documents) {
            if (document.details == null)
                continue;

            if (cookies)
                document.details.features.add(HtmlFeature.COOKIES);

            document.words.addAllSyntheticTerms(terms);
        }

        documentDeduplicator.deduplicate(ret.documents);
        calculateStatistics(ret);

        return ret;
    }

    private void fixBadCanonicalTag(CrawledDocument doc) {
        // Some sites have a canonical tag that points to a different domain,
        // but our loader can not support this, so we point these back to the
        // original url.

        var canonicalOpt = EdgeUrl.parse(doc.canonicalUrl);
        if (canonicalOpt.isEmpty()) return;

        var urlOpt = EdgeUrl.parse(doc.url);
        if (urlOpt.isEmpty()) return;

        var urlActual = urlOpt.get();
        var canonicalActual = canonicalOpt.get();

        if (!Objects.equals(urlActual.domain, canonicalActual.domain)) {
            doc.canonicalUrl = doc.url;
        }
    }

    private void calculateStatistics(ProcessedDomain ret) {
        LinkGraph linkGraph = new LinkGraph();
        TopKeywords topKeywords = new TopKeywords();

        ret.documents.forEach(topKeywords::accept);
        ret.documents.forEach(linkGraph::add);

        var invertedLinkGraph = linkGraph.invert();

        ret.documents.forEach(doc -> {
            if (doc.details != null && doc.details.metadata != null) {

                int size = linkGraph.size();
                int topology = invertedLinkGraph.numLinks(doc.url);

                doc.details.metadata = doc.details.metadata.withSize(size, topology);
            }
        });

        siteWords.flagCommonSiteWords(ret);
        siteWords.flagAdjacentWords(topKeywords, invertedLinkGraph, ret);
    }

    private DomainIndexingState getState(String crawlerStatus) {
        return switch (CrawlerDomainStatus.valueOf(crawlerStatus)) {
            case OK -> DomainIndexingState.ACTIVE;
            case REDIRECT -> DomainIndexingState.REDIR;
            case BLOCKED -> DomainIndexingState.BLOCKED;
            default -> DomainIndexingState.ERROR;
        };
    }

}
