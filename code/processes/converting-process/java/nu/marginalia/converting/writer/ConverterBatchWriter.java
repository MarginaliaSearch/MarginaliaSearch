package nu.marginalia.converting.writer;

import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.model.processed.SlopDomainRecord;
import nu.marginalia.sequence.VarintCodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Writer for a single batch of converter parquet files */
public class ConverterBatchWriter implements AutoCloseable, ConverterBatchWriterIf {
    private final SlopDomainRecord.Writer domainWriter;
    private final SlopDomainLinkRecord.Writer domainLinkWriter;
    private final SlopDocumentRecord.Writer documentWriter;

    private int ordinalOffset = 0;

    private static final Logger logger = LoggerFactory.getLogger(ConverterBatchWriter.class);

    public ConverterBatchWriter(Path basePath, int batchNumber) throws IOException {
        Path domainPath = ProcessedDataFileNames.domainFileName(basePath);
        Path linksPath = ProcessedDataFileNames.domainLinkFileName(basePath);
        Path docsPath = ProcessedDataFileNames.documentFileName(basePath);

        if (!Files.exists(domainPath)) {
            Files.createDirectory(domainPath);
        }
        if (!Files.exists(linksPath)) {
            Files.createDirectory(linksPath);
        }
        if (!Files.exists(docsPath)) {
            Files.createDirectory(docsPath);
        }

        domainWriter = new SlopDomainRecord.Writer(domainPath, batchNumber);
        domainLinkWriter = new SlopDomainLinkRecord.Writer(linksPath, batchNumber);
        documentWriter = new SlopDocumentRecord.Writer(docsPath, batchNumber);
    }


    /** Sets the lowest ordinal value for the documents in this batch */
    public void setOrdinalOffset(int ordinalOffset) {
        this.ordinalOffset = ordinalOffset;
    }

    @Override
    public void write(ConverterBatchWritableIf writable) throws IOException {
        writable.write(this);
    }

    @Override
    public void writeSideloadSource(SideloadSource sideloadSource) throws IOException {
        var domain = sideloadSource.getDomain();

        writeDomainData(domain);
        writeDocumentData(domain.domain, sideloadSource.getDocumentsStream());
    }

    @Override
    public void writeProcessedDomain(ProcessedDomain domain) {
        try {
            if (domain.documents != null) {
                writeDocumentData(domain.domain, domain.documents.iterator());
            }
            writeLinkData(domain);
            writeDomainData(domain);
        }
        catch (IOException e) {
            logger.error("Data writing job failed", e);
        }

    }

    private void writeDocumentData(EdgeDomain domain,
                                     Iterator<ProcessedDocument> documentIterator)
            throws IOException
    {

        int ordinal = ordinalOffset;

        String domainName = domain.toString();

        ByteBuffer workArea = ByteBuffer.allocate(16384);

        while (documentIterator.hasNext()) {
            var document = documentIterator.next();

            if (document.details == null || document.words == null) {
                continue;
            }

            var wb = document.words.build(workArea);

            List<VarintCodedSequence> spanSequences = new ArrayList<>(wb.spans.size());
            byte[] spanCodes = new byte[wb.spans.size()];

            for (int i = 0; i < wb.spans.size(); i++) {
                var span = wb.spans.get(i);

                spanCodes[i] = span.code();
                spanSequences.add(span.spans());
            }

            documentWriter.write(new SlopDocumentRecord(
                    domainName,
                    document.url.toString(),
                    ordinal++,
                    document.state.toString(),
                    document.stateReason,
                    document.details.title,
                    document.details.description,
                    HtmlFeature.encode(document.details.features),
                    document.details.standard.name(),
                    document.details.length,
                    document.details.hashCode,
                    (float) document.details.quality,
                    document.details.metadata.encode(),
                    document.details.pubYear,
                    wb.keywords,
                    wb.metadata,
                    wb.positions,
                    spanCodes,
                    spanSequences
            ));
        }

    }

    private void writeLinkData(ProcessedDomain domain) throws IOException {
        String from = domain.domain.toString();

        if (domain.documents == null)
            return;

        Set<EdgeDomain> seen = new HashSet<>();

        for (var doc : domain.documents) {
            if (doc.details == null)
                continue;

            for (var link : doc.details.linksExternal) {
                var dest = link.domain;

                if (!seen.add(dest)) {
                    continue;
                }

                domainLinkWriter.write(new SlopDomainLinkRecord(
                        from,
                        dest.toString()
                ));
            }
        }

        if (domain.redirect != null) {
            domainLinkWriter.write(new SlopDomainLinkRecord(
                    from,
                    domain.redirect.toString()
            ));
        }

    }

    public void writeDomainData(ProcessedDomain domain) throws IOException {
        DomainMetadata metadata = DomainMetadata.from(domain);

        List<String> feeds = getFeedUrls(domain);

        domainWriter.write(
                new SlopDomainRecord(
                        domain.domain.toString(),
                        metadata.known(),
                        metadata.good(),
                        metadata.visited(),
                        Optional.ofNullable(domain.state).map(DomainIndexingState::toString).orElse(""),
                        Optional.ofNullable(domain.redirect).map(EdgeDomain::toString).orElse(""),
                        domain.ip,
                        feeds
                )
        );
    }

    private List<String> getFeedUrls(ProcessedDomain domain) {
        var documents = domain.documents;
        if (documents == null)
            return List.of();

        return documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.feedLinks.stream())
                .distinct()
                .map(EdgeUrl::toString)
                .toList();
    }

    public void close() throws IOException {
        domainWriter.close();
        documentWriter.close();
        domainLinkWriter.close();
    }
}

record DomainMetadata(int known, int good, int visited) {

    public static DomainMetadata from(ProcessedDomain domain) {
        if (domain.sizeloadSizeAdvice != null) {
            return new DomainMetadata(
                    domain.sizeloadSizeAdvice,
                    domain.sizeloadSizeAdvice,
                    domain.sizeloadSizeAdvice
            );
        }

        var documents = domain.documents;
        if (documents == null) {
            return new DomainMetadata(0, 0, 0);
        }

        int visitedUrls = 0;
        int goodUrls = 0;
        Set<EdgeUrl> knownUrls = new HashSet<>();

        for (var doc : documents) {
            visitedUrls++;

            if (doc.isOk()) {
                goodUrls++;
            }

            knownUrls.add(doc.url);

            Optional.ofNullable(doc.details)
                    .map(details -> details.linksInternal)
                    .ifPresent(knownUrls::addAll);
        }

        return new DomainMetadata(knownUrls.size(), goodUrls, visitedUrls);
    }

}