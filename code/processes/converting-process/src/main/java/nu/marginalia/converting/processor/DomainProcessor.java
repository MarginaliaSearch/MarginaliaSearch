package nu.marginalia.converting.processor;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.crawling.model.CrawlerDomainStatus;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.links.TopKeywords;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;

import java.util.*;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final LshDocumentDeduplicator documentDeduplicator;

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           LshDocumentDeduplicator documentDeduplicator) {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.documentDeduplicator = documentDeduplicator;
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

            fixBadCanonicalTags(crawledDomain.doc);

            for (var doc : crawledDomain.doc) {
                var processedDoc = documentProcessor.process(doc, crawledDomain);

                if (processedDoc.url != null) {
                    ret.documents.add(processedDoc);
                }

            }

            documentDeduplicator.deduplicate(ret.documents);

            calculateStatistics(ret);
        }
        else {
            ret.documents = Collections.emptyList();
        }

        ret.state = getState(crawledDomain.crawlerStatus);

        return ret;
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


    private void fixBadCanonicalTags(List<CrawledDocument> docs) {
        Map<String, Set<String>> seenCanonicals = new HashMap<>();
        Set<String> seenUrls = new HashSet<>();

        // Sometimes sites set a blanket canonical link to their root page
        // this removes such links from consideration

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)) {
                seenCanonicals.computeIfAbsent(document.canonicalUrl, url -> new HashSet<>()).add(document.documentBodyHash);
            }
            seenUrls.add(document.url);
        }

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)
                    && seenCanonicals.getOrDefault(document.canonicalUrl, Collections.emptySet()).size() > 1) {

                if (seenUrls.add(document.canonicalUrl)) {
                    document.canonicalUrl = document.url;
                }
                else {
                    document.crawlerStatus = CrawlerDocumentStatus.BAD_CANONICAL.name();
                }
            }
        }

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)
                && seenCanonicals.getOrDefault(document.canonicalUrl, Collections.emptySet()).size() > 1) {
                document.canonicalUrl = document.url;
            }
        }

        // Ignore canonical URL if it points to a different domain
        // ... this confuses the hell out of the loader
        for (var document : docs) {
            if (Strings.isNullOrEmpty(document.canonicalUrl))
                continue;

            Optional<EdgeUrl> cUrl = EdgeUrl.parse(document.canonicalUrl);
            Optional<EdgeUrl> dUrl = EdgeUrl.parse(document.url);

            if (cUrl.isPresent() && dUrl.isPresent()
                    && !Objects.equals(cUrl.get().domain, dUrl.get().domain))
            {
                document.canonicalUrl = document.url;
            }
        }
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
