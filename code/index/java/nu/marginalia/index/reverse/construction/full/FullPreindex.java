package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.LongArrayFileWriter;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.index.config.ReverseIndexParameters;
import nu.marginalia.index.reverse.construction.IndexMergeOrdering;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.index.reverse.construction.PositionsFileReader;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.skiplist.SkipListWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static nu.marginalia.array.algo.TwoArrayOperations.mergeArraysN;

/** Contains the data that would go into a reverse index,
 * that is, a mapping from words to documents, minus the actual
 * index structure that makes the data quick to access while
 * searching.
 * <p>
 * Two preindexes can be merged into a third preindex containing
 * the union of their data.
 */
public class FullPreindex implements AutoCloseable {
    static final int RECORD_SIZE_LONGS = 3;

    final FullPreindexWordSegments segments;
    /** Records are (document id, encoded positions offset, term metadata). */
    final LongArray documents;
    final Path positionsFile;

    FullPreindex(FullPreindexWordSegments segments, LongArray documents, Path positionsFile) {
        this.segments = segments;
        this.documents = documents;
        this.positionsFile = positionsFile;
    }

    static boolean compressionEnabled() {
        return Boolean.parseBoolean(System.getProperty("index.compressFullPreindex", "true"));
    }

    static Optional<FullPreindexReference> mergeAll(Path workDir,
                                                    List<FullPreindexReference> runs,
                                                    boolean compress,
                                                    Executor executor)
    {
        return IndexMergeOrdering.mergeAll(executor, runs, FullPreindexReference::sizeBytes, (left, right) -> {
            try {
                var merged = merge(workDir, left, right, compress);
                left.delete();
                right.delete();
                return merged;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void finalizeIndex(Path outputFileDocs,
                              Path outputFileDocsValues,
                              Path outputFileWords,
                              Path outputFilePositions) throws IOException
    {
        LongArray wordIds = segments.wordIds;
        LongArray counts = segments.counts;

        if (counts.size() != wordIds.size())
            throw new IllegalStateException("Counts and word-ids of different size");
        if (counts.size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("counts.size() too big!");
        }

        Files.deleteIfExists(outputFileWords);

        final long wordsSize = ReverseIndexParameters.wordsBTreeContext.calculateSize((int) counts.size());
        final long positionsBaseOffset = appendPositionsFile(outputFilePositions);

        // Write the docs file
        try (var wordsArray = LongArrayFactory.mmapForWritingConfined(outputFileWords, wordsSize);
             var docsWriter = new SkipListWriter(outputFileDocs, outputFileDocsValues))
        {
            new BTreeWriter(wordsArray, ReverseIndexParameters.wordsBTreeContext)
                .write(0, (int) counts.size(), mapRegion -> {
                    long segmentStart = 0;
                    for (long i = 0; i < counts.size(); i++) {
                        int count = Math.toIntExact(counts.get(i));
                        long segmentEnd = segmentStart + RECORD_SIZE_LONGS * (long) count;
                        long docsOffset;

                        if (count == 0) {
                            docsOffset = -1;
                        }
                        else {
                            docsOffset = docsWriter.writeList(
                                    documents.range(segmentStart, segmentEnd), count, (encodedOffset) -> {
                                        if (encodedOffset == 0)
                                            return 0;

                                        // We need to re-encode the position offset with the updated base offset
                                        return PositionCodec.encode(
                                                PositionCodec.decodeSize(encodedOffset),
                                                PositionCodec.decodeOffset(encodedOffset) + positionsBaseOffset
                                        );
                                    });
                        }

                        mapRegion.set(2*i, wordIds.get(i));
                        mapRegion.set(2*i + 1, docsOffset);

                        segmentStart = segmentEnd;
                    }
                });
            wordsArray.force();
        }

        SkipListWriter.writeFooter(outputFileDocs, "skplist-docs-file");
    }

    @Override
    public void close() {
        try {
            documents.close();
        }
        finally {
            segments.close();
        }
    }

    private long appendPositionsFile(Path outputFile) throws IOException {
        try (var os = Files.newOutputStream(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {

            long positionsBaseOffset = Files.size(outputFile);

            if (Files.size(positionsFile) >= (1L << 48) - positionsBaseOffset) {
                throw new IOException("Positions file exceeds 48-bit offsets");
            }

            Files.copy(positionsFile, os);

            return positionsBaseOffset;
        }
    }

    static FullPreindexReference merge(Path directory,
                                      FullPreindexReference leftReference,
                                      FullPreindexReference rightReference,
                                      boolean compress) throws IOException
    {
        try (var left = leftReference.open();
             var right = rightReference.open())
        {
            return merge(directory, left, right, compress);
        }
    }

    public static FullPreindexReference merge(Path destDir,
                                             FullPreindex left,
                                             FullPreindex right,
                                             boolean compress) throws IOException {
        var output = FullPreindexReference.create(destDir, compress);

        var leftIter = left.segments.iterator(RECORD_SIZE_LONGS);
        var rightIter = right.segments.iterator(RECORD_SIZE_LONGS);

        try (var wordsWriter = LongArrayFileWriter.create(output.wordsFile());
             var countsWriter = LongArrayFileWriter.create(output.countsFile());
             var docsWriter = new FullPreindexDocWriter(output.documentsFile(), compress);
             var positionsWriter = new PositionsFileConstructor(output.positionsFile());
             var leftPositions = new PositionsFileReader(left.positionsFile);
             var rightPositions = new PositionsFileReader(right.positionsFile))
        {
            var plan = new MergePlan(wordsWriter, countsWriter, docsWriter, positionsWriter);

            boolean leftHasMore = leftIter.next();
            boolean rightHasMore = rightIter.next();

            while (leftHasMore && rightHasMore) {
                if (leftIter.wordId < rightIter.wordId) {
                    plan.copySegment(leftIter, left.documents, leftPositions);
                    leftHasMore = leftIter.next();
                }
                else if (rightIter.wordId < leftIter.wordId) {
                    plan.copySegment(rightIter, right.documents, rightPositions);
                    rightHasMore = rightIter.next();
                }
                else {
                    // both inputs have documents for the current word
                    plan.mergeSegments(leftIter, rightIter,
                            left.documents, right.documents,
                            leftPositions, rightPositions);

                    leftHasMore = leftIter.next();
                    rightHasMore = rightIter.next();
                }
            }

            while (leftHasMore) {
                plan.copySegment(leftIter, left.documents, leftPositions);
                leftHasMore = leftIter.next();
            }

            while (rightHasMore) {
                plan.copySegment(rightIter, right.documents, rightPositions);
                rightHasMore = rightIter.next();
            }
        }

        return output;
    }

    private record MergePlan(LongArrayFileWriter wordsWriter,
                             LongArrayFileWriter countsWriter,
                             FullPreindexDocWriter docsWriter,
                             PositionsFileConstructor positionsWriter)
    {
        void mergeSegments(FullPreindexWordSegments.SegmentIterator leftIter,
                           FullPreindexWordSegments.SegmentIterator rightIter,
                           LongArray left,
                           LongArray right,
                           PositionsFileReader leftPositions,
                           PositionsFileReader rightPositions) throws IOException
        {
            long segmentSize = mergeArraysN(RECORD_SIZE_LONGS,
                    (source, start, end) -> copyPostings(source, start, end,
                            source == left ? leftPositions : rightPositions),
                    left, right,
                    leftIter.startOffset, leftIter.endOffset,
                    rightIter.startOffset, rightIter.endOffset);

            wordsWriter.put(leftIter.wordId);
            countsWriter.put(segmentSize / RECORD_SIZE_LONGS);
        }

        // Straight copy between segments
        void copySegment(FullPreindexWordSegments.SegmentIterator sourceIter,
                         LongArray srcDocuments,
                         PositionsFileReader positions) throws IOException
        {
            copyPostings(srcDocuments, sourceIter.startOffset, sourceIter.endOffset, positions);

            wordsWriter.put(sourceIter.wordId);
            countsWriter.put(sourceIter.size() / RECORD_SIZE_LONGS);
        }

        private void copyPostings(LongArray source,
                                  long start,
                                  long end,
                                  PositionsFileReader positionsReader) throws IOException
        {
            if (start == end) {
                return;
            }

            long firstPositions = source.get(start + 1);
            long lastPositions = source.get(end - 2);
            long sourcePositionsStart = PositionCodec.decodeOffset(firstPositions);
            long sourcePositionsEnd = PositionCodec.decodeOffset(lastPositions) + PositionCodec.decodeSize(lastPositions);
            long positionsSize = sourcePositionsEnd - sourcePositionsStart;

            long destinationPositionsStart = positionsWriter.copyRange(positionsReader, sourcePositionsStart, positionsSize);
            long relocationOffset = destinationPositionsStart - sourcePositionsStart;

            for (long i = start; i < end; i += RECORD_SIZE_LONGS) {
                long encodedOffset = source.get(i + 1);
                int positionSize = PositionCodec.decodeSize(encodedOffset);
                long positionOffset = PositionCodec.decodeOffset(encodedOffset) + relocationOffset;
                long relocatedOffset = PositionCodec.encode(positionSize, positionOffset);

                long documentId = source.get(i);
                long metadata = source.get(i + 2);
                docsWriter.put(documentId, relocatedOffset, metadata);
            }
        }
    }
}
