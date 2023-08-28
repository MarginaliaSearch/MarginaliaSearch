package nu.marginalia.index.construction;

import lombok.SneakyThrows;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.index.journal.reader.IndexJournalReader;
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
 * the associated ReversePReindexWordSegments data
 */
public class ReversePreindexDocuments {
    private final Path file;
    public  final LongArray documents;
    private static final int RECORD_SIZE_LONGS = 2;
    private static final Logger logger= LoggerFactory.getLogger(ReversePreindexDocuments.class);

    public ReversePreindexDocuments(LongArray documents, Path file) {
        this.documents = documents;
        this.file = file;
    }

    public static ReversePreindexDocuments construct(
            Path docsFile,
            IndexJournalReader reader,
            DocIdRewriter docIdRewriter,
            SortingContext sortingContext,
            ReversePreindexWordSegments segments) throws IOException {


        logger.info("Transfering data");
        createUnsortedDocsFile(docsFile, reader, segments, docIdRewriter);

        LongArray docsFileMap = LongArray.mmapForWriting(docsFile, 8 * Files.size(docsFile));
        logger.info("Sorting data");
        sortDocsFile(docsFileMap, segments, sortingContext);

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
                                               IndexJournalReader reader,
                                               ReversePreindexWordSegments segments,
                                               DocIdRewriter docIdRewriter) throws IOException {
        long fileSize = 8 * segments.totalSize();
        LongArray outArray = LongArray.mmapForWriting(docsFile, fileSize);

        var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
        offsetMap.defaultReturnValue(0);

        reader.forEachDocIdRecord((docId, rec) -> {
            long wordId = rec.wordId();
            long meta = rec.metadata();

            long rankEncodedId = docIdRewriter.rewriteDocId(docId);

            long offset = offsetMap.addTo(wordId, RECORD_SIZE_LONGS);
            outArray.set(offset + 0, rankEncodedId);
            outArray.set(offset + 1, meta);
        });

        outArray.force();
    }

    @SneakyThrows
    private static void sortDocsFile(LongArray docsFileMap, ReversePreindexWordSegments segments, SortingContext sortingContext) throws IOException {

        var iter = segments.iterator(RECORD_SIZE_LONGS);

        ExecutorService sortingWorkers = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        while (iter.next()) {
            if (iter.size() < 1024) {
                docsFileMap.quickSortN(RECORD_SIZE_LONGS,
                        iter.startOffset,
                        iter.endOffset);
            }
            else {
                sortingWorkers.execute(() ->
                        docsFileMap.quickSortN(RECORD_SIZE_LONGS,
                                iter.startOffset,
                                iter.endOffset));
            }
        }

        sortingWorkers.shutdown();
        logger.info("Awaiting shutdown");

        while (!sortingWorkers.awaitTermination(1, TimeUnit.HOURS));

        sortingWorkers.close();
    }

    public void delete() throws IOException {
        Files.delete(this.file);
    }
}
