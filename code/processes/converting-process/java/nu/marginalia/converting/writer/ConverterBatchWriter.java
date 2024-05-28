package nu.marginalia.converting.writer;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import lombok.SneakyThrows;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.io.processed.DocumentRecordParquetFileWriter;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileWriter;
import nu.marginalia.io.processed.DomainRecordParquetFileWriter;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.processed.DocumentRecord;
import nu.marginalia.model.processed.DomainLinkRecord;
import nu.marginalia.model.processed.DomainRecord;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/** Writer for a single batch of converter parquet files */
public class ConverterBatchWriter implements AutoCloseable, ConverterBatchWriterIf {
    private final DomainRecordParquetFileWriter domainWriter;
    private final DomainLinkRecordParquetFileWriter domainLinkWriter;
    private final DocumentRecordParquetFileWriter documentWriter;

    private static final Logger logger = LoggerFactory.getLogger(ConverterBatchWriter.class);

    public ConverterBatchWriter(Path basePath, int batchNumber) throws IOException {
        domainWriter = new DomainRecordParquetFileWriter(
                ProcessedDataFileNames.domainFileName(basePath, batchNumber)
        );
        domainLinkWriter = new DomainLinkRecordParquetFileWriter(
                ProcessedDataFileNames.domainLinkFileName(basePath, batchNumber)
        );
        documentWriter = new DocumentRecordParquetFileWriter(
                ProcessedDataFileNames.documentFileName(basePath, batchNumber)
        );
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
    @SneakyThrows
    public void writeProcessedDomain(ProcessedDomain domain) {
        var results = ForkJoinPool.commonPool().invokeAll(
                writeTasks(domain)
        );

        for (var result : results) {
            if (result.state() == Future.State.FAILED) {
                logger.warn("Parquet writing job failed", result.exceptionNow());
            }
        }
    }

    private List<Callable<Object>> writeTasks(ProcessedDomain domain) {
        return List.of(
                () -> writeDocumentData(domain),
                () -> writeLinkData(domain),
                () -> writeDomainData(domain)
        );
    }

    private Object writeDocumentData(ProcessedDomain domain) throws IOException {
        if (domain.documents == null)
            return this;

        writeDocumentData(domain.domain, domain.documents.iterator());

        return this;
    }

    private void writeDocumentData(EdgeDomain domain,
                                     Iterator<ProcessedDocument> documentIterator)
            throws IOException
    {

        int ordinal = 0;

        String domainName = domain.toString();

        while (documentIterator.hasNext()) {
            var document = documentIterator.next();
            if (document.details == null) {
                new DocumentRecord(
                        domainName,
                        document.url.toString(),
                        ordinal,
                        document.state.toString(),
                        document.stateReason,
                        null,
                        null,
                        0,
                        null,
                        0,
                        0L,
                        -15,
                        0L,
                        null,
                        null,
                        null,
                        null);
            }
            else {
                var wb = document.words.build();
                List<String> words = Arrays.asList(wb.keywords);
                TLongArrayList metas = new TLongArrayList(wb.metadata);
                List<RoaringBitmap> positions = Arrays.asList(wb.positions);

                documentWriter.write(new DocumentRecord(
                        domainName,
                        document.url.toString(),
                        ordinal,
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
                        words,
                        metas,
                        positions
                ));

            }

            ordinal++;
        }

    }

    private Object writeLinkData(ProcessedDomain domain) throws IOException {
        String from = domain.domain.toString();

        if (domain.documents == null)
            return this;

        Set<EdgeDomain> seen = new HashSet<>();

        for (var doc : domain.documents) {
            if (doc.details == null)
                continue;

            for (var link : doc.details.linksExternal) {
                var dest = link.domain;

                if (!seen.add(dest)) {
                    continue;
                }

                domainLinkWriter.write(new DomainLinkRecord(
                        from,
                        dest.toString()
                ));
            }
        }

        if (domain.redirect != null) {
            domainLinkWriter.write(new DomainLinkRecord(
                    from,
                    domain.redirect.toString()
            ));
        }

        return this;
    }

    public Object writeDomainData(ProcessedDomain domain) throws IOException {
        DomainMetadata metadata = DomainMetadata.from(domain);

        List<String> feeds = getFeedUrls(domain);

        domainWriter.write(
                new DomainRecord(
                        domain.domain.toString(),
                        metadata.known(),
                        metadata.good(),
                        metadata.visited(),
                        Optional.ofNullable(domain.state).map(DomainIndexingState::toString).orElse(null),
                        Optional.ofNullable(domain.redirect).map(EdgeDomain::toString).orElse(null),
                        domain.ip,
                        feeds
                )
        );

        return this;
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