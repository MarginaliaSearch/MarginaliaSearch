package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.*;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.geoip.sources.AsnTable;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.converting.processor.logic.links.TopKeywords;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;
import nu.marginalia.model.crawl.HtmlFeature;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final AnchorTagsSource anchorTagsSource;
    private final AnchorTextKeywords anchorTextKeywords;
    private final LshDocumentDeduplicator documentDeduplicator;
    private final GeoIpDictionary geoIpDictionary;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           AnchorTagsSourceFactory anchorTagsSourceFactory,
                           AnchorTextKeywords anchorTextKeywords,
                           LshDocumentDeduplicator documentDeduplicator, GeoIpDictionary geoIpDictionary) throws SQLException
    {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.anchorTextKeywords = anchorTextKeywords;
        this.documentDeduplicator = documentDeduplicator;
        this.anchorTagsSource = anchorTagsSourceFactory.create();
        this.geoIpDictionary = geoIpDictionary;

        geoIpDictionary.waitReady();
    }

    @SneakyThrows
    @Nullable
    public ProcessedDomain process(SerializableCrawlDataStream dataStream) {
        if (!dataStream.hasNext()) {
            return null;
        }

        var ret = new ProcessedDomain();
        List<ProcessedDocument> docs = new ArrayList<>();
        Set<String> processedUrls = new HashSet<>();

        boolean cookies = false;
        String ip = "";

        DomainLinks externalDomainLinks = null;

        while (dataStream.hasNext()) {
            var data = dataStream.next();

            // Do a lazy load of the external domain links since we don't know the domain
            // until we see the first document
            if (externalDomainLinks == null) {
                var domain = data.getDomain();

                if (domain != null) {
                    externalDomainLinks = anchorTagsSource.getAnchorTags(domain);
                }
            }

            if (data instanceof CrawledDomain crawledDomain) {
                ret.domain = new EdgeDomain(crawledDomain.domain);
                ret.ip = crawledDomain.ip;

                cookies = crawledDomain.hasCookies();
                ip = crawledDomain.ip;

                if (crawledDomain.redirectDomain != null) {
                    ret.redirect = new EdgeDomain(crawledDomain.redirectDomain);
                }
                ret.documents = docs;
                ret.state = getState(crawledDomain.crawlerStatus);
            }
            else if (data instanceof CrawledDocument doc) {
                try {
                    if (doc.url == null || !processedUrls.add(doc.url))
                        continue;

                    if (Boolean.TRUE.equals(doc.hasCookies)) {
                        cookies = true;
                    }

                    // This case should never be reachable, as we should have initiated
                    // the externalDomainLinks variable above if we made it past the
                    // doc.url == null check; but we'll leave it here just in case
                    // to make debugging easier if we break this.
                    assert externalDomainLinks != null : "externalDomainLinks has not been initialized";

                    docs.add(documentProcessor.process(doc, externalDomainLinks));
                }
                catch (Exception ex) {
                    logger.warn("Failed to process " + doc.url, ex);
                }
            }
        }

        // Add late keywords and features from domain-level information

        List<String> terms = new ArrayList<>();

        addIpInfo(terms, ip);

        if (cookies) {
            terms.add(HtmlFeature.COOKIES.getKeyword());
        }

        if (isAcademicDomain(ret.domain)) {
            terms.add("special:academia");
        }

        for (var document : ret.documents) {
            if (document.details == null)
                continue;

            if (cookies) {
                document.details.features.add(HtmlFeature.COOKIES);
            }

            document.words.addAllSyntheticTerms(terms);

            document.words.addAnchorTerms(
                    anchorTextKeywords.getAnchorTextKeywords(externalDomainLinks, document.url)
            );
        }
        documentDeduplicator.deduplicate(ret.documents);
        calculateStatistics(ret, externalDomainLinks);

        return ret;
    }

    private void addIpInfo(List<String> terms, String ip) {
        terms.add("ip:"+ip);

        // Add IP location country as a term
        String country = geoIpDictionary.getCountry(ip);
        if (!country.isBlank()) { // use the ip:-prefix as there's no real confusion between e.g. ip:127.0.0.1 and ip:uk
            terms.add("ip:"+country.toLowerCase());
        }

        // Add ASN as a term
        geoIpDictionary.getAsnInfo(ip).ifPresent(asnInfo -> {
            terms.add("as:"+asnInfo.asn());

            for (var orgPart : StringUtils.split(asnInfo.org(), '-')) {
                terms.add("as:"+orgPart.toLowerCase());
            }

            if (isCloudy(asnInfo)) {
                terms.add("special:cloud");
            }
        });


    }

    private boolean isCloudy(AsnTable.AsnInfo asnInfo) {
        String org = asnInfo.org();

        if (org.contains("MICROSOFT-AZURE")) {
            return true;
        }
        if(org.contains("AMAZON")) {
            return true;
        }
        if (org.contains("CLOUDFLARE")) {
            return true;
        }
        if (org.contains("GOOGLE-CLOUD")) {
            return true;
        }
        if (org.contains("DIGITALOCEAN")) {
            return true;
        }
        if (org.contains("ALIBABA")) {
            return true;
        }
        if (org.contains("CLOUDFLARE")) {
            return true;
        }

        return false;
    }


    private static final Pattern academicPattern = Pattern.compile(".*\\.(ac|edu)\\.[a-z]{2}$");
    private boolean isAcademicDomain(EdgeDomain domain) {

        if (domain.topDomain.endsWith(".edu"))
            return true;

        if (academicPattern.matcher(domain.topDomain).matches())
            return true;

        return false;
    }

    private void calculateStatistics(ProcessedDomain ret, DomainLinks externalDomainLinks) {
        LinkGraph linkGraph = new LinkGraph();
        TopKeywords topKeywords = new TopKeywords();

        ret.documents.forEach(topKeywords::accept);
        ret.documents.forEach(linkGraph::add);

        var invertedLinkGraph = linkGraph.invert();

        ret.documents.forEach(doc -> {
            if (doc.details == null)
                return;
            if (doc.details.metadata == null)
                return;

            int size = linkGraph.size();
            int topology = invertedLinkGraph.numLinks(doc.url)
                         + externalDomainLinks.countForUrl(doc.url);

            doc.details.metadata = doc.details.metadata.withSizeAndTopology(size, topology);
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
