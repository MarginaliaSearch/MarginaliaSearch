package nu.marginalia.converting.writer;

import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDocumentFinal;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.model.processed.SlopDomainRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

/** Writer for a single batch of converter parquet files */
public class ConverterBatchWriter implements AutoCloseable, ConverterBatchWriterIf {
    private final SlopDomainRecord.Writer domainWriter;
    private final SlopDomainLinkRecord.Writer domainLinkWriter;
    private final SlopDocumentRecord.Writer documentWriter;

    private final ForkJoinPool writePool = new ForkJoinPool(4);
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

        try {
            List<Callable<Void>> tasks = List.of(
                    () -> { writeDomainData(domain); return null; },
                    () -> { writeDocumentData(domain.domain, sideloadSource.getDocumentsStream()); return null; }
            );
            for (var outcome : writePool.invokeAll(tasks)) {
                if (outcome.state() == Future.State.FAILED) {
                    throw new IOException(outcome.exceptionNow());
                }
            }
        }
        catch (IOException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void writeProcessedDomain(ProcessedDomain domain) {
        try {
            try {
                List<Callable<Void>> tasks = List.of(
                        () -> { writeDomainData(domain); return null; },
                        () -> { if (domain.documents != null) writeDocumentData(domain.domain, domain.documents.iterator()); return null; },
                        () -> { writeLinkData(domain); return null; }
                );
                for (var outcome : writePool.invokeAll(tasks)) {
                    if (outcome.state() == Future.State.FAILED) {
                        throw new IOException(outcome.exceptionNow());
                    }
                }
            }
            catch (IOException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        catch (IOException e) {
            logger.error("Data writing job failed", e);
        }

    }

    private void writeDocumentData(EdgeDomain domain,
                                     Iterator<ProcessedDocumentFinal> documentIterator)
            throws IOException
    {

        int ordinal = ordinalOffset;

        String domainName = domain.toString();

        while (documentIterator.hasNext()) {
            var document = documentIterator.next();

            if (document.details == null || document.words == null) {
                continue;
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
                    document.details.format.name(),
                    document.details.length,
                    document.details.hashCode,
                    (float) document.details.quality,
                    document.details.metadata.encode(),
                    document.details.languageIsoCode,
                    document.details.pubYear,
                    document.words.keywords(),
                    document.words.metadata(),
                    document.words.positions(),
                    document.words.spanCodes(),
                    document.words.spanSequences()
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


        domainWriter.write(
                new SlopDomainRecord(
                        domain.domain.toString(),
                        metadata.known(),
                        metadata.good(),
                        metadata.visited(),
                        Optional.ofNullable(domain.state).map(DomainIndexingState::toString).orElse(""),
                        Optional.ofNullable(domain.redirect).map(EdgeDomain::toString).orElse(""),
                        domain.ip
                )
        );
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