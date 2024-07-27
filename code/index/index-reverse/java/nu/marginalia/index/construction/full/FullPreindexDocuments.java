package nu.marginalia.index.construction.full;

import lombok.SneakyThrows;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.PositionsFileConstructor;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.rwf.RandomFileAssembler;
import nu.marginalia.slop.desc.SlopTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
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
public class FullPreindexDocuments {
    public final LongArray documents;

    private static PositionsFileConstructor positionsFileConstructor;
    private static final int RECORD_SIZE_LONGS = 2;
    private static final Logger logger = LoggerFactory.getLogger(FullPreindexDocuments.class);

    public final Path file;

    public FullPreindexDocuments(LongArray documents, Path file) {
        this.documents = documents;
        this.file = file;
    }

    public static FullPreindexDocuments construct(
            Path docsFile,
            Path workDir,
            IndexJournalPage journalInstance,
            DocIdRewriter docIdRewriter,
            PositionsFileConstructor positionsFileConstructor,
            FullPreindexWordSegments segments) throws IOException {
        FullPreindexDocuments.positionsFileConstructor = positionsFileConstructor;

        createUnsortedDocsFile(docsFile, workDir, journalInstance, segments, docIdRewriter);

        LongArray docsFileMap = LongArrayFactory.mmapForModifyingShared(docsFile);
        sortDocsFile(docsFileMap, segments);

        return new FullPreindexDocuments(docsFileMap, docsFile);
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
                                               IndexJournalPage journalInstance,
                                               FullPreindexWordSegments segments,
                                               DocIdRewriter docIdRewriter) throws IOException {

        long fileSizeLongs = RECORD_SIZE_LONGS * segments.totalSize();

        final ByteBuffer tempBuffer = ByteBuffer.allocate(65536);

        try (var assembly = RandomFileAssembler.create(workDir, fileSizeLongs);
             var slopTable = new SlopTable())
        {
            var docIds = journalInstance.openCombinedId(slopTable);
            var termCounts = journalInstance.openTermCounts(slopTable);
            var termIds = journalInstance.openTermIds(slopTable);
            var termMeta = journalInstance.openTermMetadata(slopTable);
            var positions = journalInstance.openTermPositions(slopTable);

            var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);

            while (termCounts.hasRemaining()) {
                long docId = docIds.get();
                long rankEncodedId = docIdRewriter.rewriteDocId(docId);

                long termCount = termCounts.get();

                for (int termIdx = 0; termIdx < termCount; termIdx++) {
                    long termId = termIds.get();
                    byte meta = termMeta.get();

                    // Read positions
                    tempBuffer.clear();
                    positions.getData(tempBuffer);
                    tempBuffer.flip();

                    long offset = offsetMap.addTo(termId, RECORD_SIZE_LONGS);
                    long encodedPosOffset = positionsFileConstructor.add(meta, tempBuffer);

                    assembly.put(offset + 0, rankEncodedId);
                    assembly.put(offset + 1, encodedPosOffset);
                }
            }

            assembly.write(docsFile);
        }
    }

    @SneakyThrows
    private static void sortDocsFile(LongArray docsFileMap, FullPreindexWordSegments segments) throws IOException {

        var iter = segments.iterator(RECORD_SIZE_LONGS);

        ExecutorService sortingWorkers = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        while (iter.next()) {
            long iterStart = iter.startOffset;
            long iterEnd = iter.endOffset;

            if (iter.size() < 1024) {
                docsFileMap.quickSortN(RECORD_SIZE_LONGS, iterStart, iterEnd);
            }
            else {
                sortingWorkers.execute(() ->
                    docsFileMap.quickSortN(RECORD_SIZE_LONGS, iterStart, iterEnd));
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
