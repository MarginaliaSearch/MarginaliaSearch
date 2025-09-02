package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.rwf.RandomFileAssembler;
import nu.marginalia.slop.SlopTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
            IndexJournalPage journalInstance,
            DocIdRewriter docIdRewriter,
            PrioPreindexWordSegments segments) throws IOException {

        createUnsortedDocsFile(docsFile, workDir, journalInstance, segments, docIdRewriter);

        LongArray docsFileMap = LongArrayFactory.mmapForModifyingShared(docsFile);
        sortDocsFile(docsFileMap, segments);

        return new PrioPreindexDocuments(docsFileMap, docsFile);
    }

    public FileChannel createDocumentsFileChannel() throws IOException {
        return (FileChannel) Files.newByteChannel(file, StandardOpenOption.READ);
    }


    public long size() {
        return documents.size();
    }

    private static void createUnsortedDocsFile(Path docsFile,
                                               Path workDir,
                                               IndexJournalPage instance,
                                               PrioPreindexWordSegments segments,
                                               DocIdRewriter docIdRewriter) throws IOException {

        long fileSizeLongs = RECORD_SIZE_LONGS * segments.totalSize();

        try (var assembly = RandomFileAssembler.create(workDir, fileSizeLongs);
             var slopTable = new SlopTable(instance.baseDir(), instance.page()))
        {
            var docIds = instance.openCombinedId(slopTable);
            var termIds = instance.openTermIds(slopTable);
            var termMeta = instance.openTermMetadata(slopTable);

            var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);


            while (docIds.hasRemaining()) {
                long docId = docIds.get();
                long rankEncodedId = docIdRewriter.rewriteDocId(docId);

                long[] tIds = termIds.get();
                byte[] tMeta = termMeta.get();

                for (int i = 0; i < tIds.length; i++) {
                    long termId = tIds[i];
                    byte meta = tMeta[i];

                    if (meta != 0) {
                        long offset = offsetMap.addTo(termId, RECORD_SIZE_LONGS);
                        assembly.put(offset, rankEncodedId);
                    }
                }
            }

            assembly.write(docsFile);
        }
    }

    private static void sortDocsFile(LongArray docsFileMap, PrioPreindexWordSegments segments) {

        var iter = segments.iterator(RECORD_SIZE_LONGS);

        while (iter.next()) {
            long iterStart = iter.startOffset;
            long iterEnd = iter.endOffset;

            docsFileMap.sort(iterStart, iterEnd);
        }
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
