package nu.marginalia.wmsa.edge.converting.processor;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;
import nu.marginalia.wmsa.edge.converting.processor.logic.CommonKeywordExtractor;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDomainStatus;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

import java.util.*;

import static nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus.BAD_CANONICAL;

public class DomainProcessor {
    private static final CommonKeywordExtractor commonKeywordExtractor = new CommonKeywordExtractor();

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

            fixBadCanonicalTags(crawledDomain.doc);

            DocumentDisqualifier disqualifier = new DocumentDisqualifier();
            for (var doc : crawledDomain.doc) {
                if (disqualifier.isQualified()) {
                    var processedDoc = documentProcessor.process(doc, crawledDomain);

                    if (processedDoc.url != null) {
                        ret.documents.add(processedDoc);
                        processedDoc.quality().ifPresent(disqualifier::offer);
                    }
                    else if ("LANGUAGE".equals(processedDoc.stateReason)) {
                        disqualifier.offer(-100);
                    }
                }
                else { // Short-circuit processing if quality is too low
                    var stub = documentProcessor.makeDisqualifiedStub(doc);
                    if (stub.url != null) {
                        ret.documents.add(stub);
                    }
                }
            }

            Set<String> commonSiteWords = new HashSet<>(10);

            commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(ret, IndexBlock.Tfidf_Top, IndexBlock.Subjects));
            commonSiteWords.addAll(commonKeywordExtractor.getCommonSiteWords(ret, IndexBlock.Title));

            if (!commonSiteWords.isEmpty()) {
                for (var doc : ret.documents) {
                    if (doc.words != null) {
                        doc.words.get(IndexBlock.Site).addAll(commonSiteWords);
                    }
                }
            }
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
            if (!Strings.isNullOrEmpty(document.canonicalUrl) && !Objects.equals(document.canonicalUrl, document.url)) {
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

            if (cUrl.isPresent() && dUrl.isPresent() && !Objects.equals(cUrl.get().domain, dUrl.get().domain)) {
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

    class DocumentDisqualifier {
        int count;
        int goodCount;

        void offer(double quality) {
            count++;
            if (quality > minAvgDocumentQuality) {
                goodCount++;
            }
        }

        boolean isQualified() {
            return count < 25 || goodCount*10 >= count;
        }
    }
}
