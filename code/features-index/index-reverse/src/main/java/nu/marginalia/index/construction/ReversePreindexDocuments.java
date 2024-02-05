package nu.marginalia.index.construction;

import lombok.SneakyThrows;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
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
 * the associated ReversePreindexWordSegments data
 */
public class ReversePreindexDocuments {
    final Path file;
    public  final LongArray documents;
    private static final int RECORD_SIZE_LONGS = 2;
    private static final Logger logger = LoggerFactory.getLogger(ReversePreindexDocuments.class);

    public ReversePreindexDocuments(LongArray documents, Path file) {
        this.documents = documents;
        this.file = file;
    }

    public static ReversePreindexDocuments construct(
            Path docsFile,
            Path workDir,
            IndexJournalReader reader,
            DocIdRewriter docIdRewriter,
            ReversePreindexWordSegments segments) throws IOException {

        createUnsortedDocsFile(docsFile, workDir, reader, segments, docIdRewriter);

        LongArray docsFileMap = LongArrayFactory.mmapForModifyingShared(docsFile);
        sortDocsFile(docsFileMap, segments);

        return new ReversePreindexDocuments(docsFileMap, docsFile);
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
                                               ReversePreindexWordSegments segments,
                                               DocIdRewriter docIdRewriter) throws IOException {

        long fileSizeLongs = RECORD_SIZE_LONGS * segments.totalSize();

        try (RandomFileAssembler assembly = RandomFileAssembler.create(workDir, fileSizeLongs)) {

            var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);

            var pointer = reader.newPointer();
            while (pointer.nextDocument()) {
                long rankEncodedId = docIdRewriter.rewriteDocId(pointer.documentId());
                while (pointer.nextRecord()) {
                    long wordId = pointer.wordId();
                    long wordMeta = pointer.wordMeta();

                    long offset = offsetMap.addTo(wordId, RECORD_SIZE_LONGS);

                    assembly.put(offset + 0, rankEncodedId);
                    assembly.put(offset + 1, wordMeta);
                }
            }

            assembly.write(docsFile);
        }
    }

    @SneakyThrows
    private static void sortDocsFile(LongArray docsFileMap, ReversePreindexWordSegments segments) throws IOException {

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
