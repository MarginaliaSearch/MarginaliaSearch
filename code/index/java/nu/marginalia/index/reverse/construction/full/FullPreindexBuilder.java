package nu.marginalia.index.reverse.construction.full;

import it.unimi.dsi.fastutil.longs.LongArrays;
import nu.marginalia.array.LongArrayFileWriter;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.slop.SlopTable;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Writes postings and their position payloads in the same order so merges can
 * read both sequentially. Bounded runs limit the memory needed for sorting.
 */
final class FullPreindexBuilder {
    private final int maxPositionBytes = Integer.getInteger("index.positionRunBytes", 64 * 1024 * 1024);
    private final int maxPostings = Integer.getInteger("index.positionRunRecords", 2 * 1024 * 1024);

    private final Path directory;
    private final boolean compress;
    private final List<FullPreindexReference> runs = new ArrayList<>();

    private long[] termIds;
    private long[] documentIds;
    private long[] termMetadata;
    private long[] positionOffsets;
    private int[] postingOrder;
    private byte[] positionData;

    private int postingCount;
    private int positionByteCount;

    private FullPreindexBuilder(Path directory, boolean compress) {
        if (maxPositionBytes < 65536 || maxPostings < 1) {
            throw new IllegalArgumentException("Position runs need at least 65536 bytes and one record");
        }

        this.directory = directory;
        this.compress = compress;

        final int capacity = Math.min(4096, maxPostings);
        termIds = new long[capacity];
        documentIds = new long[capacity];
        termMetadata = new long[capacity];
        positionOffsets = new long[capacity];
        postingOrder = new int[capacity];
        positionData = new byte[65536];
    }

    static List<FullPreindexReference> constructRuns(IndexJournalPage journal,
                                                    DocIdRewriter rewriter,
                                                    Path directory,
                                                    boolean compress) throws IOException
    {
        var builder = new FullPreindexBuilder(directory, compress);

        try (var arena = Arena.ofConfined();
             SlopTable table = new SlopTable(journal.baseDir(), journal.page()))
        {
            // The journal reader needs contiguous space for one document's encoded positions.
            ByteBuffer documentPositions = arena.allocate(100 * 1024 * 1024).asByteBuffer();

            var docIdReader = journal.openCombinedId(table);
            var termIdReader = journal.openTermIds(table);
            var termMetadataReader = journal.openTermMetadata(table);
            var positionsReader = journal.openTermPositions(table);

            while (docIdReader.hasRemaining()) {
                long documentId = rewriter.rewriteDocId(docIdReader.get());
                long[] termIds = termIdReader.get();
                long[] termMetadata = termMetadataReader.get();
                List<ByteBuffer> positions = positionsReader.getData(documentPositions);

                for (int i = 0; i < termIds.length; i++) {
                    builder.add(termIds[i], documentId, termMetadata[i], positions.get(i));
                }
            }
        }

        builder.flush();
        return builder.runs;
    }

    private void add(long termId, long documentId, long metadata, ByteBuffer positionBytes) throws IOException {
        int positionSize = positionBytes.remaining();
        if (positionSize > 0xFFFF) {
            throw new IOException("Position sequence exceeds 65535 bytes");
        }

        if (postingCount == maxPostings || positionSize > maxPositionBytes - positionByteCount) {
            flush();
        }

        if (postingCount == termIds.length) {
            int newCapa = (int) Math.min(maxPostings, 2L * termIds.length);

            termIds = Arrays.copyOf(termIds, newCapa);
            documentIds = Arrays.copyOf(documentIds, newCapa);
            termMetadata = Arrays.copyOf(termMetadata, newCapa);
            positionOffsets = Arrays.copyOf(positionOffsets, newCapa);
            postingOrder = Arrays.copyOf(postingOrder, newCapa);
        }

        if (positionByteCount + positionSize > positionData.length) {
            long requiredCapacity = Math.max(positionByteCount + positionSize, 2L * positionData.length);
            int newCapa = (int) Math.min(maxPositionBytes, requiredCapacity);
            positionData = Arrays.copyOf(positionData, newCapa);
        }

        termIds[postingCount] = termId;
        documentIds[postingCount] = documentId;
        termMetadata[postingCount] = metadata;
        positionOffsets[postingCount] = PositionCodec.encode(positionSize, positionByteCount);
        postingOrder[postingCount] = postingCount;

        positionBytes.get(positionData, positionByteCount, positionSize);
        positionByteCount += positionSize;
        postingCount++;
    }

    private void flush() throws IOException {
        if (postingCount == 0 && !runs.isEmpty()) {
            return;
        }

        LongArrays.radixSortIndirect(postingOrder, termIds, documentIds, 0, postingCount, true);

        var output = FullPreindexReference.create(directory, compress);
        runs.add(output);

        try (var wordsWriter = LongArrayFileWriter.create(output.wordsFile());
             var countsWriter = LongArrayFileWriter.create(output.countsFile());
             var docsWriter = new FullPreindexDocWriter(output.documentsFile(), compress);
             var positionsWriter = new PositionsFileConstructor(output.positionsFile()))
        {
            long currentTermId = 0;
            long termDocumentCount = 0;

            for (int i = 0; i < postingCount; i++) {
                int postingIndex = postingOrder[i];

                if (termDocumentCount != 0 && termIds[postingIndex] != currentTermId) {
                    wordsWriter.put(currentTermId);
                    countsWriter.put(termDocumentCount);
                    termDocumentCount = 0;
                }

                currentTermId = termIds[postingIndex];
                termDocumentCount++;

                long encodedOffset = positionOffsets[postingIndex];
                int bufferOffset = (int) PositionCodec.decodeOffset(encodedOffset);
                int positionSize = PositionCodec.decodeSize(encodedOffset);

                long fileOffset = positionsWriter.add(positionData, bufferOffset, positionSize);
                docsWriter.put(documentIds[postingIndex], fileOffset, termMetadata[postingIndex]);
            }

            if (termDocumentCount != 0) {
                wordsWriter.put(currentTermId);
                countsWriter.put(termDocumentCount);
            }
        }

        postingCount = 0;
        positionByteCount = 0;
    }

}
