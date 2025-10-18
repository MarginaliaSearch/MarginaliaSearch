package nu.marginalia.index.reverse.construction.full;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.rwf.RandomFileAssembler;
import nu.marginalia.sequence.slop.VarintCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A LongArray with document data, segmented according to
 * the associated FullPreindexWordSegments data
 */
public class FullPreindexDocuments {
    public final LongArray documents;

    private static PositionsFileConstructor positionsFileConstructor;
    public static final int RECORD_SIZE_LONGS = 3;
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


        long fileSizeLongs = RECORD_SIZE_LONGS * segments.totalSize();

        final ByteBuffer tempBuffer = ByteBuffer.allocate(1024*1024*100);

        try (RandomFileAssembler assembly = RandomFileAssembler.create(workDir, fileSizeLongs);
             SlopTable slopTable = new SlopTable(journalInstance.baseDir(), journalInstance.page()))
        {
            LongColumn.Reader docIds = journalInstance.openCombinedId(slopTable);
            LongArrayColumn.Reader termIds = journalInstance.openTermIds(slopTable);
            LongArrayColumn.Reader termMeta = journalInstance.openTermMetadata(slopTable);
            VarintCodedSequenceArrayColumn.Reader positions = journalInstance.openTermPositions(slopTable);

            Long2LongOpenHashMap offsetMap = segments.asMap(RECORD_SIZE_LONGS);
            offsetMap.defaultReturnValue(0);

            PositionsFileConstructor.PositionsFileBlock positionsBlock = positionsFileConstructor.getBlock();

            while (docIds.hasRemaining()) {
                long docId = docIds.get();
                long rankEncodedId = docIdRewriter.rewriteDocId(docId);

                long[] tIds = termIds.get();
                long[] tMeta = termMeta.get();
                tempBuffer.clear();
                List<ByteBuffer> tPos = positions.getData(tempBuffer);

                for (int i = 0; i < tIds.length; i++) {
                    long termId = tIds[i];
                    long meta = tMeta[i];
                    ByteBuffer pos = tPos.get(i);

                    long offset = offsetMap.addTo(termId, RECORD_SIZE_LONGS);
                    long encodedPosOffset = positionsFileConstructor.add(positionsBlock, pos);

                    assembly.put(offset + 0, rankEncodedId);
                    assembly.put(offset + 1, encodedPosOffset);
                    assembly.put(offset + 2, meta);
                }
            }

            positionsBlock.commit();
            assembly.write(docsFile);
        }

        LongArray docsFileMap = LongArrayFactory.mmapForModifyingShared(docsFile);
        FullPreindexWordSegments.SegmentIterator iter = segments.iterator(RECORD_SIZE_LONGS);

        while (iter.next()) {
            long iterStart = iter.startOffset;
            long iterEnd = iter.endOffset;

            docsFileMap.quickSortN(RECORD_SIZE_LONGS, iterStart, iterEnd);
        }

        return new FullPreindexDocuments(docsFileMap, docsFile);
    }

    public LongArray slice(long start, long end) {
        return documents.range(start, end);
    }

    public long size() {
        return documents.size();
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
