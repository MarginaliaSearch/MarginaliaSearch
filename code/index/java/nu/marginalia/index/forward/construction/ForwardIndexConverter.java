package nu.marginalia.index.forward.construction;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.config.ForwardIndexParameters;
import nu.marginalia.index.forward.doctext.DocTextsWriter;
import nu.marginalia.index.forward.spans.IndexSpansWriter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.model.FeaturesCodec;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.sequence.slop.VarintCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import org.roaringbitmap.longlong.LongConsumer;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static nu.marginalia.index.config.ForwardIndexParameters.ForwardIndexVersion.*;

public class ForwardIndexConverter {

    private final ProcessHeartbeat heartbeat;

    private static final ForwardIndexParameters.ForwardIndexVersion VERSION = V2026_08__1;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path outputFileDocsId;
    private final Path outputFileDocsData;
    private final Collection<IndexJournal> journals;
    private final DomainRankings domainRankings;

    private final Path outputFileSpansData;
    private final Path outputFileDocTextsData;

    public ForwardIndexConverter(ProcessHeartbeat heartbeat,
                                 Path outputFileDocsId,
                                 Path outputFileDocsData,
                                 Path outputFileSpansData,
                                 Path outputFileDocTextsData,
                                 Collection<IndexJournal> journals,
                                 DomainRankings domainRankings
                                 ) {
        this.heartbeat = heartbeat;
        this.outputFileDocsId = outputFileDocsId;
        this.outputFileDocsData = outputFileDocsData;
        this.outputFileSpansData = outputFileSpansData;
        this.outputFileDocTextsData = outputFileDocTextsData;
        this.journals = journals;
        this.domainRankings = domainRankings;
    }

    public enum TaskSteps {
        GET_DOC_IDS,
        GATHER_OFFSETS,
        SUPPLEMENTAL_INDEXES,
        DOMAIN_METADATA,
        FORCE,
        FINISHED
    }

    public void convert() throws IOException {
        deleteOldFiles();

        logger.info("Domain Rankings size = {}", domainRankings.size());

        try (var progress = heartbeat.createProcessTaskHeartbeat(TaskSteps.class, "forwardIndexConverter");
             var spansWriter = new IndexSpansWriter(outputFileSpansData);
             var docTextsWriter = new DocTextsWriter(outputFileDocTextsData)
        ) {
            progress.progress(TaskSteps.GET_DOC_IDS);

            LongArray docsFileId = getDocIds(outputFileDocsId, journals);

            progress.progress(TaskSteps.GATHER_OFFSETS);

            // doc ids -> sorted list of ids

            Long2IntOpenHashMap docIdToIdx = new Long2IntOpenHashMap((int) docsFileId.size());
            docsFileId.forEach(0, docsFileId.size(), (pos, val) -> docIdToIdx.put(val, (int) pos));

            progress.progress(TaskSteps.SUPPLEMENTAL_INDEXES);

            // docIdToIdx -> file offset for id

            final int entrySize = VERSION.entrySize;

            LongArray docFileData = LongArrayFactory.mmapForWritingConfined(outputFileDocsData,
                    entrySize * docsFileId.size() + 1 /* <-- footer */);

            ByteBuffer workArea = ByteBuffer.allocate(1024*1024*100);
            for (IndexJournal journal : journals) {
                for (IndexJournalPage instance : journal.pages()) {
                    try (var slopTable = new SlopTable(instance.baseDir(), instance.page())) {
                        var docIdReader = instance.openCombinedId(slopTable);
                        var metaReader = instance.openDocumentMeta(slopTable);
                        var featuresReader = instance.openFeatures(slopTable);
                        var sizeReader = instance.openSize(slopTable);
                        var pubDateReader = instance.openPubDate(slopTable);

                        var spansCodesReader = instance.openSpanCodes(slopTable);
                        var spansSeqReader = instance.openSpans(slopTable);
                        var docTextZstdReader = instance.openDocumentTextZstd(slopTable);

                        while (docIdReader.hasRemaining()) {
                            long docId = docIdReader.get();
                            int domainId = UrlIdCodec.getDomainId(docId);

                            long entryOffset = (long) entrySize * docIdToIdx.get(docId);

                            int ranking = domainRankings.getRanking(domainId);
                            long meta = DocumentMetadata.encodeRank(metaReader.get(), ranking);

                            final int docFeatures = featuresReader.get();
                            final int docSize = sizeReader.get();
                            final short pubDate = pubDateReader.get();

                            long features = FeaturesCodec.encode(
                                    docFeatures,
                                    docSize,
                                    pubDate);

                            // Write spans
                            long encodedSpansOffset = writeSpans(spansWriter, workArea, spansCodesReader, spansSeqReader);

                            // Write the compressed document text
                            long encodedDocTextOffset = docTextsWriter.write(docTextZstdReader.get());

                            // Write the principal forward documents file
                            docFileData.set(entryOffset + ForwardIndexParameters.METADATA_OFFSET, meta);
                            docFileData.set(entryOffset + ForwardIndexParameters.FEATURES_OFFSET, features);
                            docFileData.set(entryOffset + ForwardIndexParameters.SPANS_OFFSET, encodedSpansOffset);
                            docFileData.set(entryOffset + ForwardIndexParameters.DOC_TEXT_OFFSET, encodedDocTextOffset);

                        }
                    }
                }
            }

            docFileData.set(docFileData.size() - 1,
                    ForwardIndexParameters.encodeFooter(VERSION)
            );

            progress.progress(TaskSteps.FORCE);

            docFileData.force();
            docsFileId.force();

            docFileData.close();
            docsFileId.close();

            progress.progress(TaskSteps.DOMAIN_METADATA);

            // Save a copy of the domain rankings as they look at the time of index construction

            domainRankings.save(outputFileDocsData.getParent());

            progress.progress(TaskSteps.FINISHED);
        } catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        }
    }

    private static long writeSpans(IndexSpansWriter spansWriter,
                                   ByteBuffer workArea,
                                   ByteArrayColumn.Reader spansCodesReader,
                                   VarintCodedSequenceArrayColumn.Reader spansSeqReader) throws IOException {

        byte[] spansCodes = spansCodesReader.get();

        // Start a new record
        spansWriter.beginRecord(spansCodes.length);

        // For each span, write its code and start,end pairs
        List<ByteBuffer> spans = spansSeqReader.getData(workArea);
        for (int i = 0; i < spansCodes.length; i++) {
            spansWriter.writeSpan(spansCodes[i], spans.get(i));
        }

        // Finalize the record
        return spansWriter.endRecord();
    }

    private LongArray getDocIds(Path outputFileDocs, Collection<IndexJournal> journalReaders) throws IOException {
        Roaring64Bitmap rbm = new Roaring64Bitmap();

        for (IndexJournal journalReader : journalReaders) {
            for (var instance : journalReader.pages()) {
                try (var slopTable = new SlopTable(instance.baseDir(), instance.page())) {
                    LongColumn.Reader idReader = instance.openCombinedId(slopTable);

                    while (idReader.hasRemaining()) {
                        rbm.add(idReader.get());
                    }
                }
            }
        }

        LongArray ret = LongArrayFactory.mmapForWritingConfined(outputFileDocs, rbm.getIntCardinality());
        rbm.forEach(new LongConsumer() {
            int offset;
            @Override
            public void accept(long value) {
                ret.set(offset++, value);
            }
        });

        return ret;
    }

    private void deleteOldFiles() throws IOException {
        Files.deleteIfExists(outputFileDocsId);
        Files.deleteIfExists(outputFileDocsData);
        Files.deleteIfExists(outputFileDocTextsData);
    }

}

