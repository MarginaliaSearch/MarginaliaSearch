package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import nu.marginalia.api.domsample.DomSampleClient;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.classifier.adblock.DomSampleClassifier;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;
import nu.marginalia.converting.processor.logic.links.LinkGraph;
import nu.marginalia.converting.processor.logic.links.TopKeywords;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.writer.ConverterBatchWritableIf;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.geoip.sources.AsnTable;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import nu.marginalia.util.ProcessingIterator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final AnchorTagsSource anchorTagsSource;
    private final GeoIpDictionary geoIpDictionary;
    private final DomSampleClient domSampleClient;
    private final DomSampleClassifier domSampleClassifier;
    private final ExecutorService domSampleExecutor = Executors.newCachedThreadPool();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           AnchorTagsSourceFactory anchorTagsSourceFactory,
                           DomSampleClient domSampleClient,
                           GeoIpDictionary geoIpDictionary,
                           DomSampleClassifier domSampleClassifier) throws SQLException, InterruptedException {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.anchorTagsSource = anchorTagsSourceFactory.create();
        this.geoIpDictionary = geoIpDictionary;
        this.domSampleClient = domSampleClient;
        this.domSampleClassifier = domSampleClassifier;

        geoIpDictionary.waitReady();
        domSampleClient.waitReady(Duration.ofMinutes(5));
    }

    public SimpleProcessing simpleProcessing(SerializableCrawlDataStream dataStream, int sizeHint, Collection<String> extraKeywords) {
        try {
            return new SimpleProcessing(dataStream, sizeHint, extraKeywords);
        }
        catch (Exception ex) {
            logger.warn("Failed to process domain sideload", ex);
            return null;
        }
    }

    public SimpleProcessing simpleProcessing(SerializableCrawlDataStream dataStream, int sizeHint) {
        try {
            return new SimpleProcessing(dataStream, sizeHint);
        }
        catch (Exception ex) {
            logger.warn("Failed to process domain sideload", ex);
            return null;
        }
    }

    @Nullable
    public ProcessedDomain fullProcessing(SerializableCrawlDataStream dataStream) {
        try {
            if (!dataStream.hasNext()) {
                return null;
            }

            Set<String> processedUrls = new HashSet<>();

            if (!(dataStream.next() instanceof CrawledDomain crawledDomain)) {
                throw new IllegalStateException("First record must be a domain, was " + dataStream.next().getClass().getSimpleName());
            }

            CompletableFuture<Set<DomSampleClassifier.DomSampleClassification>> domSample =
                    domSampleClient.getSampleAsync(crawledDomain.domain, domSampleExecutor)
                            .thenApply(domSampleClassifier::classify);

            DomainLinks externalDomainLinks = anchorTagsSource.getAnchorTags(crawledDomain.getDomain());
            DocumentDecorator documentDecorator = new DocumentDecorator();

            // Process Domain Record

            ProcessedDomain ret = new ProcessedDomain();
            processDomain(crawledDomain, ret, documentDecorator);
            ret.documents = new ArrayList<>();

            // Process Documents

            List<DomSampleClassifier.DomSampleClassification> classifiers = null;

            try (var deduplicator = new LshDocumentDeduplicator()) {
                while (dataStream.hasNext()) {
                    if (!(dataStream.next() instanceof CrawledDocument doc))
                        continue;
                    if (doc.url == null)
                        continue;
                    if (doc.documentBodyBytes.length == 0)
                        continue;
                    if (!processedUrls.add(doc.url))
                        continue;

                    try {
                        var processedDoc = documentProcessor.process(doc, ret.domain, externalDomainLinks, documentDecorator);

                        if (deduplicator.isDocumentDuplicate(processedDoc)) {
                            processedDoc.state = UrlIndexingState.DISQUALIFIED;
                            processedDoc.stateReason = "Duplicate";
                        }

                        if (processedDoc.isOk()) {
                            if (null == classifiers) {
                                try {
                                    classifiers = new ArrayList<>(domSample.get());
                                }
                                catch (Exception ex) {
                                    classifiers = List.of();
                                }
                            }

                            classifiers.forEach(classifier -> {
                                classifier.apply(processedDoc.details, processedDoc.words);
                            });
                        }

                        ret.documents.add(processedDoc);
                    } catch (Exception ex) {
                        logger.warn("Failed to process " + doc.url, ex);
                    }
                }
            }

            // Add late keywords and features from domain-level information

            calculateStatistics(ret, externalDomainLinks);

            return ret;
        }
        catch (Exception ex) {
            logger.warn("Failed to process domain", ex);
            return null;
        }
    }

    /** The simple processing track processes documents individually, and does not perform any domain-level analysis.
     *  This is needed to process extremely large domains, which would otherwise eat up too much RAM.
     */
    public class SimpleProcessing implements ConverterBatchWritableIf, SideloadSource {
        private final SerializableCrawlDataStream dataStream;
        private final ProcessedDomain domain;
        private final DocumentDecorator documentDecorator;
        private final Set<String> processedUrls = new HashSet<>();
        private final DomainLinks externalDomainLinks;
        private final LshDocumentDeduplicator deduplicator = new LshDocumentDeduplicator();

        CompletableFuture<Set<DomSampleClassifier.DomSampleClassification>> domSample;
        AtomicReference<List<DomSampleClassifier.DomSampleClassification>> classification;

        private static final ProcessingIterator.Factory iteratorFactory = ProcessingIterator.factory(8,
                Integer.getInteger("java.util.concurrent.ForkJoinPool.common.parallelism", Runtime.getRuntime().availableProcessors())
        );

        SimpleProcessing(SerializableCrawlDataStream dataStream, int sizeHint) throws IOException {
            this(dataStream, sizeHint, List.of());
        }

        SimpleProcessing(SerializableCrawlDataStream dataStream, int sizeHint, Collection<String> extraKeywords) throws IOException {
            this.dataStream = dataStream;

            if (!dataStream.hasNext() || !(dataStream.next() instanceof CrawledDomain crawledDomain))
            {
                throw new IllegalStateException("First record must be a domain, was " + dataStream.next().getClass().getSimpleName());
            }

            domain = new ProcessedDomain();
            domain.sizeloadSizeAdvice = sizeHint == 0 ? 10_000 : sizeHint;

            documentDecorator = new DocumentDecorator();
            documentDecorator.addTerms(extraKeywords);

            processDomain(crawledDomain, domain, documentDecorator);

            domSample = domSampleClient.getSampleAsync(crawledDomain.domain, domSampleExecutor)
                            .thenApply(domSampleClassifier::classify);

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
                            if (deduplicator.isDocumentDuplicate(processedDoc)) {
                                processedDoc.state = UrlIndexingState.DISQUALIFIED;
                                processedDoc.stateReason = "Duplicate";
                            }
                        }

                        if (processedDoc.isProcessedFully()) {
                            // This is a bit sketchy, but we need to set the size and topology to something
                            processedDoc.details.metadata = processedDoc.details.metadata.withSizeAndTopology(
                                    10_000, externalDomainLinks.countForUrl(processedDoc.url));

                            // Apply classifications
                            try {
                                domSample.get().forEach(classification -> {
                                    classification.apply(processedDoc.details, processedDoc.words);
                                });
                            }
                            catch (Exception ex) {
                            }
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
            if (!domSample.isDone()) { // Cancel in the case there were nothing to try to consume this
                domSample.cancel(true);
            }

            dataStream.close();
            deduplicator.close();
        }
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
