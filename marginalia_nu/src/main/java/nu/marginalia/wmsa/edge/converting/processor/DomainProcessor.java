package nu.marginalia.wmsa.edge.converting.processor;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import nu.marginalia.util.StringPool;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;
import nu.marginalia.wmsa.edge.converting.processor.logic.InternalLinkGraph;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDomainStatus;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

import java.util.*;

import static nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus.BAD_CANONICAL;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords) {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
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

            StringPool stringPool = new StringPool(1000 + 100 * crawledDomain.doc.size());

            for (var doc : crawledDomain.doc) {
                var processedDoc = documentProcessor.process(doc, crawledDomain);

                if (processedDoc.words != null) {
                    // The word data is extremely redundant, and may encompass something like
                    // 5,000,000 words per domain (and multiple domains are processed at the same time).

                    processedDoc.words.internalize(stringPool::internalize);
                }

                if (processedDoc.url != null) {
                    ret.documents.add(processedDoc);
                }

            }

            stringPool.flush();

            InternalLinkGraph internalLinkGraph = new InternalLinkGraph();

            ret.documents.forEach(internalLinkGraph::accept);
            ret.documents.forEach(doc -> {
                if (doc.details != null && doc.details.metadata != null) {
                    doc.details.metadata = doc.details.metadata.withSize(internalLinkGraph.numKnownUrls());
                }
            });

            siteWords.flagCommonSiteWords(ret);
            siteWords.flagAdjacentWords(internalLinkGraph, ret);

        }
        else {
            ret.documents = Collections.emptyList();
        }

        ret.state = getState(crawledDomain.crawlerStatus);

        return ret;
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
                    document.crawlerStatus = BAD_CANONICAL.name();
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

    private EdgeDomainIndexingState getState(String crawlerStatus) {
        return switch (CrawlerDomainStatus.valueOf(crawlerStatus)) {
            case OK -> EdgeDomainIndexingState.ACTIVE;
            case REDIRECT -> EdgeDomainIndexingState.REDIR;
            case BLOCKED -> EdgeDomainIndexingState.BLOCKED;
            default -> EdgeDomainIndexingState.ERROR;
        };
    }

}
