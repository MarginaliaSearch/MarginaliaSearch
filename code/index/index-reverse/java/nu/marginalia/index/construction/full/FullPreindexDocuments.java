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
import java.util.List;

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

        final ByteBuffer tempBuffer = ByteBuffer.allocate(1024*1024*100);

        try (var assembly = RandomFileAssembler.create(workDir, fileSizeLongs);
             var slopTable = new SlopTable())
        {
            var docIds = journalInstance.openCombinedId(slopTable);
            var termIds = journalInstance.openTermIds(slopTable);
            var termMeta = journalInstance.openTermMetadata(slopTable);
            var positions = journalInstance.openTermPositions(slopTable);

            var offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);

            while (docIds.hasRemaining()) {
                long docId = docIds.get();
                long rankEncodedId = docIdRewriter.rewriteDocId(docId);

                long[] tIds = termIds.get();
                byte[] tMeta = termMeta.get();
                tempBuffer.clear();
                List<ByteBuffer> tPos = positions.getData(tempBuffer);

                for (int i = 0; i < tIds.length; i++) {
                    long termId = tIds[i];
                    byte meta = tMeta[i];
                    ByteBuffer pos = tPos.get(i);

                    long offset = offsetMap.addTo(termId, RECORD_SIZE_LONGS);
                    long encodedPosOffset = positionsFileConstructor.add(meta, pos);

                    assembly.put(offset + 0, rankEncodedId);
                    assembly.put(offset + 1, encodedPosOffset);
                }
            }

            assembly.write(docsFile);
        }
    }

    @SneakyThrows
    private static void sortDocsFile(LongArray docsFileMap, FullPreindexWordSegments segments) {

        var iter = segments.iterator(RECORD_SIZE_LONGS);

        while (iter.next()) {
            long iterStart = iter.startOffset;
            long iterEnd = iter.endOffset;

            docsFileMap.quickSortN(RECORD_SIZE_LONGS, iterStart, iterEnd);
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
