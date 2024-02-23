package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.writer.ConverterBatchWritableIf;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.*;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.geoip.sources.AsnTable;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.converting.processor.logic.links.TopKeywords;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;
import nu.marginalia.util.ProcessingIterator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class DomainProcessor {
    private static final int SIDELOAD_THRESHOLD = Integer.getInteger("converter.sideloadThreshold", 10_000);
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final AnchorTagsSource anchorTagsSource;
    private final AnchorTextKeywords anchorTextKeywords;
    private final GeoIpDictionary geoIpDictionary;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           AnchorTagsSourceFactory anchorTagsSourceFactory,
                           AnchorTextKeywords anchorTextKeywords,
                           GeoIpDictionary geoIpDictionary) throws SQLException
    {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.anchorTextKeywords = anchorTextKeywords;
        this.anchorTagsSource = anchorTagsSourceFactory.create();
        this.geoIpDictionary = geoIpDictionary;

        geoIpDictionary.waitReady();
    }

    public ConverterBatchWritableIf createWritable(SerializableCrawlDataStream domain) {
        final int sizeHint = domain.sizeHint();

        if (sizeHint > SIDELOAD_THRESHOLD) {
            // If the file is too big, we run a processing mode that doesn't
            // require loading the entire dataset into RAM
            return sideloadProcessing(domain, sizeHint);
        }

        return fullProcessing(domain);
    }

    public SideloadProcessing sideloadProcessing(SerializableCrawlDataStream dataStream, int sizeHint) {
        try {
            return new SideloadProcessing(dataStream, sizeHint);
        }
        catch (Exception ex) {
            logger.warn("Failed to process domain sideload", ex);
            return null;
        }

    }

    public class SideloadProcessing implements ConverterBatchWritableIf, SideloadSource {
        private final SerializableCrawlDataStream dataStream;
        private final ProcessedDomain domain;
        private final DocumentDecorator documentDecorator;
        private final Set<String> processedUrls = new HashSet<>();
        private final DomainLinks externalDomainLinks;
        private final LshDocumentDeduplicator deduplicator = new LshDocumentDeduplicator();
        private static final ProcessingIterator.Factory iteratorFactory = ProcessingIterator.factory(8,
                Integer.getInteger("java.util.concurrent.ForkJoinPool.common.parallelism", Runtime.getRuntime().availableProcessors())
        );

        SideloadProcessing(SerializableCrawlDataStream dataStream, int sizeHint) throws IOException {
            this.dataStream = dataStream;

            if (!dataStream.hasNext() || !(dataStream.next() instanceof CrawledDomain crawledDomain))
            {
                throw new IllegalStateException("First record must be a domain, was " + dataStream.next().getClass().getSimpleName());
            }

            domain = new ProcessedDomain();
            domain.sizeloadSizeAdvice = sizeHint == 0 ? 10_000 : sizeHint;

            documentDecorator = new DocumentDecorator(anchorTextKeywords);

            processDomain(crawledDomain, domain, documentDecorator);

            externalDomainLinks = anchorTagsSource.getAnchorTags(domain.domain);
        }

        @Override
        public ProcessedDomain getDomain() {
            return domain;
        }

        @Override
        public Iterator<ProcessedDocument> getDocumentsStream() {
            return iteratorFactory.create((taskConsumer) -> {
                while (dataStream.hasNext())
                {
                    if (!(dataStream.next() instanceof CrawledDocument doc))
                        continue;
                    if (doc.url == null || !processedUrls.add(doc.url))
                        continue;


                    taskConsumer.accept(() -> {
                        var processedDoc = documentProcessor.process(doc, domain.domain, externalDomainLinks, documentDecorator);

                        synchronized (deduplicator) {
                            deduplicator.markIfDuplicate(processedDoc);
                        }

                        if (processedDoc.isProcessedFully()) {
                            // This is a bit sketchy, but we need to set the size and topology to something
                            processedDoc.details.metadata = processedDoc.details.metadata.withSizeAndTopology(
                                    10_000, externalDomainLinks.countForUrl(processedDoc.url));
                        }

                        return processedDoc;
                    });
                }
            });
        }

        @Override
        public void write(ConverterBatchWriter writer) throws IOException {
            writer.writeSideloadSource(this);
        }

        @Override
        public String id() {
            return domain.domain.toString();
        }

        @Override
        public void close() throws Exception {
            dataStream.close();
            deduplicator.close();
        }
    }


    @SneakyThrows
    @Nullable
    public ProcessedDomain fullProcessing(SerializableCrawlDataStream dataStream) {
        if (!dataStream.hasNext()) {
            return null;
        }

        List<ProcessedDocument> docs = new ArrayList<>();
        Set<String> processedUrls = new HashSet<>();

        if (!(dataStream.next() instanceof CrawledDomain crawledDomain)) {
            throw new IllegalStateException("First record must be a domain, was " + dataStream.next().getClass().getSimpleName());
        }

        DomainLinks externalDomainLinks = anchorTagsSource.getAnchorTags(crawledDomain.getDomain());
        DocumentDecorator documentDecorator = new DocumentDecorator(anchorTextKeywords);

        // Process Domain Record

        ProcessedDomain ret = new ProcessedDomain();
        processDomain(crawledDomain, ret, documentDecorator);
        ret.documents = docs;

        // Process Documents

        try (var deduplicator = new LshDocumentDeduplicator()) {
            while (dataStream.hasNext()) {
                if (!(dataStream.next() instanceof CrawledDocument doc))
                    continue;
                if (doc.url == null)
                    continue;
                if (!processedUrls.add(doc.url))
                    continue;

                try {
                    var processedDoc = documentProcessor.process(doc, ret.domain, externalDomainLinks, documentDecorator);
                    deduplicator.markIfDuplicate(processedDoc);
                    docs.add(processedDoc);
                } catch (Exception ex) {
                    logger.warn("Failed to process " + doc.url, ex);
                }
            }
        }

        // Add late keywords and features from domain-level information

        calculateStatistics(ret, externalDomainLinks);

        return ret;
    }

    private void processDomain(CrawledDomain crawledDomain,
                                          ProcessedDomain domain,
                                          DocumentDecorator decorator)
    {
        domain.domain = new EdgeDomain(crawledDomain.domain);
        domain.ip = crawledDomain.ip;

        addIpInfo(decorator, crawledDomain.ip);

        if (isAcademicDomain(domain.domain)) {
            decorator.addTerm("special:academia");
        }

        if (crawledDomain.redirectDomain != null) {
            domain.redirect = new EdgeDomain(crawledDomain.redirectDomain);
        }
        domain.state = getState(crawledDomain.crawlerStatus);
    }


    private void addIpInfo(DocumentDecorator decorator, String ip) {
        decorator.addTerm("ip:"+ip);

        // Add IP location country as a term
        String country = geoIpDictionary.getCountry(ip);
        if (!country.isBlank()) { // use the ip:-prefix as there's no real confusion between e.g. ip:127.0.0.1 and ip:uk
            decorator.addTerm("ip:"+country.toLowerCase());
        }

        // Add ASN as a term
        geoIpDictionary.getAsnInfo(ip).ifPresent(asnInfo -> {
            decorator.addTerm("as:"+asnInfo.asn());

            for (var orgPart : StringUtils.split(asnInfo.org(), '-')) {
                decorator.addTerm("as:"+orgPart.toLowerCase());
            }

            if (isCloudy(asnInfo)) {
                decorator.addTerm("special:cloud");
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
