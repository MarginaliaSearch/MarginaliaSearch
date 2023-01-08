package nu.marginalia.wmsa.edge.index.postings.forward;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReader;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;
import org.roaringbitmap.IntConsumer;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.wmsa.edge.index.postings.forward.ForwardIndexParameters.*;

public class ForwardIndexConverter {
    private static final int RWF_BIN_SIZE = 10_000_000;

    private final Path tmpFileDir;
    private final File inputFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path outputFileDocsId;
    private final Path outputFileDocsData;


    public ForwardIndexConverter(Path tmpFileDir,
                                 File inputFile,
                                 Path outputFileDocsId,
                                 Path outputFileDocsData
                                 ) {
        this.tmpFileDir = tmpFileDir;
        this.inputFile = inputFile;
        this.outputFileDocsId = outputFileDocsId;
        this.outputFileDocsData = outputFileDocsData;
    }

    public void convert() throws IOException {
        deleteOldFiles();

        SearchIndexJournalReaderSingleFile journalReader = new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(inputFile.toPath()));
        if (journalReader.fileHeader.fileSize() <= SearchIndexJournalReader.FILE_HEADER_SIZE_BYTES) {
            return;
        }

        logger.info("Converting  {} {}",inputFile, journalReader.fileHeader);

        final Path intermediateDocsFile = Files.createTempFile(tmpFileDir, "words-sorted", ".dat");

        try {
            LongArray docsFileId = getDocIds(outputFileDocsId, journalReader);

            // doc ids -> sorted list of ids

            logger.info("Gathering Offsets");
            Long2IntOpenHashMap docIdToIdx = new Long2IntOpenHashMap((int) docsFileId.size());
            docsFileId.forEach(0, docsFileId.size(), (pos, val) -> docIdToIdx.put(val, (int) pos));

            // docIdToIdx -> file offset for id

            logger.info("Creating Supplementary Indexes");

            LongArray docFileData = LongArray.mmapForWriting(outputFileDocsData, ENTRY_SIZE * docsFileId.size());

            journalReader.forEach(entry -> {
                long entryOffset = (long) ENTRY_SIZE * docIdToIdx.get(entry.urlId());

                docFileData.set(entryOffset + METADATA_OFFSET, entry.docMeta());
                docFileData.set(entryOffset + DOMAIN_OFFSET, entry.domainId());
            });

            docFileData.force();


        } catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        }
        finally {
            Files.deleteIfExists(intermediateDocsFile);
        }
    }

    private LongArray getDocIds(Path outputFileDocs, SearchIndexJournalReader journalReader) throws IOException {
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

