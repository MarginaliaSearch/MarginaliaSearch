package nu.marginalia.index.forward;

import com.upserve.uppend.blobs.NativeIO;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.array.LongArray;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleCompressedFile;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.ranking.DomainRankings;
import org.roaringbitmap.IntConsumer;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForwardIndexConverter {

    private final File inputFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path outputFileDocsId;
    private final Path outputFileDocsData;
    private final DomainRankings domainRankings;


    public ForwardIndexConverter(
                                 File inputFile,
                                 Path outputFileDocsId,
                                 Path outputFileDocsData,
                                 DomainRankings domainRankings
                                 ) {
        this.inputFile = inputFile;
        this.outputFileDocsId = outputFileDocsId;
        this.outputFileDocsData = outputFileDocsData;
        this.domainRankings = domainRankings;
    }

    public void convert() throws IOException {
        deleteOldFiles();

        IndexJournalReaderSingleCompressedFile journalReader = new IndexJournalReaderSingleCompressedFile(inputFile.toPath());
        if (journalReader.fileHeader().fileSize() <= IndexJournalReader.FILE_HEADER_SIZE_BYTES) {
            logger.warn("Bailing: Journal is empty!");
            return;
        }

        logger.info("Converting  {} {}", inputFile, journalReader.fileHeader);

        logger.info("Domain Rankings size = {}", domainRankings.size());

        try {
            LongArray docsFileId = getDocIds(outputFileDocsId, journalReader);

            // doc ids -> sorted list of ids

            logger.info("Gathering Offsets");
            Long2IntOpenHashMap docIdToIdx = new Long2IntOpenHashMap((int) docsFileId.size());
            docsFileId.forEach(0, docsFileId.size(), (pos, val) -> docIdToIdx.put(val, (int) pos));

            // docIdToIdx -> file offset for id

            logger.info("Creating Supplementary Indexes");

            LongArray docFileData = LongArray.mmapForWriting(outputFileDocsData, ForwardIndexParameters.ENTRY_SIZE * docsFileId.size());

            journalReader.forEach(entry -> {
                long entryOffset = (long) ForwardIndexParameters.ENTRY_SIZE * docIdToIdx.get(entry.urlId());

                int ranking = domainRankings.getRanking(entry.domainId());
                long meta = EdgePageDocumentsMetadata.encodeRank(entry.docMeta(), ranking);

                docFileData.set(entryOffset + ForwardIndexParameters.METADATA_OFFSET, meta);
                docFileData.set(entryOffset + ForwardIndexParameters.DOMAIN_OFFSET, entry.domainId());
            });

            docFileData.force();
            docsFileId.force();

            docFileData.advice(NativeIO.Advice.DontNeed);
            docsFileId.advice(NativeIO.Advice.DontNeed);
        } catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        }
    }

    private LongArray getDocIds(Path outputFileDocs, IndexJournalReader journalReader) throws IOException {
        RoaringBitmap rbm = new RoaringBitmap();
        journalReader.forEachUrlId(rbm::add);

        LongArray ret = LongArray.mmapForWriting(outputFileDocs, rbm.getCardinality());
        rbm.forEach(new IntConsumer() {
            int offset;
            @Override
            public void accept(int value) {
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

