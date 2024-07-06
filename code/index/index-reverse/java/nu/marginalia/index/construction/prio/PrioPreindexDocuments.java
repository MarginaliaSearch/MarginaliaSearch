package nu.marginalia.index.construction.prio;

import lombok.SneakyThrows;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.rwf.RandomFileAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A LongArray with document data, segmented according to
 * the associated FullPreindexWordSegments data
 */
public class PrioPreindexDocuments {
    public final LongArray documents;

    private static final int RECORD_SIZE_LONGS = 1;
    private static final Logger logger = LoggerFactory.getLogger(PrioPreindexDocuments.class);

    public final Path file;

    public PrioPreindexDocuments(LongArray documents, Path file) {
        this.documents = documents;
        this.file = file;
    }

    public static PrioPreindexDocuments construct(
            Path docsFile,
            Path workDir,
            IndexJournalReader reader,
            DocIdRewriter docIdRewriter,
            PrioPreindexWordSegments segments) throws IOException {

        createUnsortedDocsFile(docsFile, workDir, reader, segments, docIdRewriter);

        LongArray docsFileMap = LongArrayFactory.mmapForModifyingShared(docsFile);
        sortDocsFile(docsFileMap, segments);

        return new PrioPreindexDocuments(docsFileMap, docsFile);
    }

    public FileChannel createDocumentsFileChannel() throws IOException {
        return (FileChannel) Files.newByteChannel(file, StandardOpenOption.READ);
    }


    public LongArray slice(long start, long end) {
        return documents.range(start, end);
    }

    public long size() {
        return documents.size();
    }

    private static void createUnsortedDocsFile(Path docsFile,
                                               Path workDir,
                                               IndexJournalReader reader,
                                               PrioPreindexWordSegments segments,
                                               DocIdRewriter docIdRewriter) throws IOException {

        long fileSizeLongs = RECORD_SIZE_LONGS * segments.totalSize();

        try (var assembly = RandomFileAssembler.create(workDir, fileSizeLongs);
             var pointer = reader.newPointer())
        {

            var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);

            while (pointer.nextDocument()) {
                long rankEncodedId = docIdRewriter.rewriteDocId(pointer.documentId());
                for (var termData : pointer) {
                    long termId = termData.termId();

                    long offset = offsetMap.addTo(termId, RECORD_SIZE_LONGS);

                    assembly.put(offset, rankEncodedId);
                }
            }

            assembly.write(docsFile);
        }
    }

    @SneakyThrows
    private static void sortDocsFile(LongArray docsFileMap, PrioPreindexWordSegments segments) throws IOException {

        var iter = segments.iterator(RECORD_SIZE_LONGS);

        ExecutorService sortingWorkers = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        while (iter.next()) {
            long iterStart = iter.startOffset;
            long iterEnd = iter.endOffset;

            if (iter.size() < 1024) {
                docsFileMap.sort(iterStart, iterEnd);
            }
            else {
                sortingWorkers.execute(() -> docsFileMap.sort(iterStart, iterEnd));
            }
        }

        sortingWorkers.shutdown();
        while (!sortingWorkers.awaitTermination(1, TimeUnit.HOURS));

        sortingWorkers.close();
    }

    public void delete() throws IOException {
        Files.delete(this.file);
        documents.close();
    }

    public void close() {
        documents.close();
    }

    public void force() {
        documents.force();
    }
}
