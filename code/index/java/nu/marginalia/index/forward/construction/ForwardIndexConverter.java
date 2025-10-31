package nu.marginalia.index.forward.construction;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.config.ForwardIndexParameters;
import nu.marginalia.index.forward.spans.IndexSpansWriter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.slop.SlopTable;
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

public class ForwardIndexConverter {

    private final ProcessHeartbeat heartbeat;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path outputFileDocsId;
    private final Path outputFileDocsData;
    private final Collection<IndexJournal> journals;
    private final DomainRankings domainRankings;

    private final Path outputFileSpansData;

    public ForwardIndexConverter(ProcessHeartbeat heartbeat,
                                 Path outputFileDocsId,
                                 Path outputFileDocsData,
                                 Path outputFileSpansData,
                                 Collection<IndexJournal> journals,
                                 DomainRankings domainRankings
                                 ) {
        this.heartbeat = heartbeat;
        this.outputFileDocsId = outputFileDocsId;
        this.outputFileDocsData = outputFileDocsData;
        this.outputFileSpansData = outputFileSpansData;
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
             var spansWriter = new IndexSpansWriter(outputFileSpansData)
        ) {
            progress.progress(TaskSteps.GET_DOC_IDS);

            LongArray docsFileId = getDocIds(outputFileDocsId, journals);

            progress.progress(TaskSteps.GATHER_OFFSETS);

            // doc ids -> sorted list of ids

            Long2IntOpenHashMap docIdToIdx = new Long2IntOpenHashMap((int) docsFileId.size());
            docsFileId.forEach(0, docsFileId.size(), (pos, val) -> docIdToIdx.put(val, (int) pos));

            progress.progress(TaskSteps.SUPPLEMENTAL_INDEXES);

            // docIdToIdx -> file offset for id

            LongArray docFileData = LongArrayFactory.mmapForWritingConfined(outputFileDocsData, ForwardIndexParameters.ENTRY_SIZE * docsFileId.size());

            ByteBuffer workArea = ByteBuffer.allocate(1024*1024*100);
            for (IndexJournal journal : journals) {
                for (IndexJournalPage instance : journal.pages()) {
                    try (var slopTable = new SlopTable(instance.baseDir(), instance.page())) {
                        var docIdReader = instance.openCombinedId(slopTable);
                        var metaReader = instance.openDocumentMeta(slopTable);
                        var featuresReader = instance.openFeatures(slopTable);
                        var sizeReader = instance.openSize(slopTable);

                        var spansCodesReader = instance.openSpanCodes(slopTable);
                        var spansSeqReader = instance.openSpans(slopTable);

                        while (docIdReader.hasRemaining()) {
                            long docId = docIdReader.get();
                            int domainId = UrlIdCodec.getDomainId(docId);

                            long entryOffset = (long) ForwardIndexParameters.ENTRY_SIZE * docIdToIdx.get(docId);

                            int ranking = domainRankings.getRanking(domainId);
                            long meta = DocumentMetadata.encodeRank(metaReader.get(), ranking);

                            final int docFeatures = featuresReader.get();
                            final int docSize = sizeReader.get();

                            long features = docFeatures | ((long) docSize << 32L);

                            // Write spans data
                            byte[] spansCodes = spansCodesReader.get();

                            spansWriter.beginRecord(spansCodes.length);
                            workArea.clear();
                            List<ByteBuffer> spans = spansSeqReader.getData(workArea);

                            for (int i = 0; i < spansCodes.length; i++) {
                                spansWriter.writeSpan(spansCodes[i], spans.get(i));
                            }
                            long encodedSpansOffset = spansWriter.endRecord();


                            // Write the principal forward documents file
                            docFileData.set(entryOffset + ForwardIndexParameters.METADATA_OFFSET, meta);
                            docFileData.set(entryOffset + ForwardIndexParameters.FEATURES_OFFSET, features);
                            docFileData.set(entryOffset + ForwardIndexParameters.SPANS_OFFSET, encodedSpansOffset);

                        }
                    }
                }
            }

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
    }

}

