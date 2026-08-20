package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.LongArrayFileWriter;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.index.config.ReverseIndexParameters;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.skiplist.SkipListWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.array.algo.TwoArrayOperations.mergeArraysN;

/** Contains the data that would go into a reverse index,
 * that is, a mapping from words to documents, minus the actual
 * index structure that makes the data quick to access while
 * searching.
 * <p>
 * Two preindexes can be merged into a third preindex containing
 * the union of their data.  This operation requires no additional
 * RAM.
 */
public class FullPreindex {
    final FullPreindexWordSegments segments;
    final FullPreindexDocuments documents;

    public FullPreindex(FullPreindexWordSegments segments, FullPreindexDocuments documents) {
        this.segments = segments;
        this.documents = documents;
    }

    /** Constructs a new preindex with the data associated with reader.  The backing files
     * will have randomly assigned names.
     */
    public static FullPreindex constructPreindex(IndexJournalPage journalInstance,
                                                 PositionsFileConstructor positionsFileConstructor,
                                                 DocIdRewriter docIdRewriter,
                                                 Path workDir) throws IOException
    {
        Path segmentWordsFile = Files.createTempFile(workDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(workDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(workDir, "docs", ".dat");

        var segments = FullPreindexWordSegments.construct(journalInstance, segmentWordsFile, segmentCountsFile);
        var docs = FullPreindexDocuments.construct(docsFile, workDir, journalInstance, docIdRewriter, positionsFileConstructor, segments);
        return new FullPreindex(segments, docs);
    }

    /**  Close the associated memory mapped areas and return
     * a dehydrated page of this object that can be re-opened
     * later.
     */
    public FullPreindexReference closeToReference() {
        try {
            return new FullPreindexReference(segments, documents);
        }
        finally {
            documents.force();
            segments.close();
            documents.close();
        }
    }

    /** Transform the preindex into a reverse index */
    public void finalizeIndex(Path outputFileDocs,
                              Path outputFileDocsValues,
                              Path outputFileWords) throws IOException
    {
        LongArray wordIds = segments.wordIds;
        LongArray counts = segments.counts;

        if (counts.size() != wordIds.size())
            throw new IllegalStateException("Counts and word-ids of different size");
        if (counts.size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("counts.size() too big!");
        }

        Files.deleteIfExists(outputFileWords);

        long wordsSize = ReverseIndexParameters.wordsBTreeContext.calculateSize((int) counts.size());
        LongArray wordsArray = LongArrayFactory.mmapForWritingConfined(outputFileWords, wordsSize);

        // Write the docs file
        try (var transformer = new FullIndexSkipListTransformer(outputFileDocs, outputFileDocsValues, documents.documents))
        {
            new BTreeWriter(wordsArray, ReverseIndexParameters.wordsBTreeContext)
                .write(0, (int) counts.size(), mapRegion -> {
                    long segmentEnd = 0;
                    for (long i = 0; i < counts.size(); i++) {
                        segmentEnd += FullPreindexDocuments.RECORD_SIZE_LONGS * counts.get(i);

                        mapRegion.set(2*i, wordIds.get(i));
                        mapRegion.set(2*i + 1, transformer.transform(i, segmentEnd));
                    }
                });
        }

        wordsArray.force();
        wordsArray.close();

        SkipListWriter.writeFooter(outputFileDocs, "skplist-docs-file");
    }

    /** Delete all files associated with this pre-index */
    public void delete() throws IOException {
        segments.delete();
        documents.delete();
    }

    public static FullPreindexReference merge(Path destDir,
                                              FullPreindex left,
                                              FullPreindex right) throws IOException {

        Path wordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path countsFile = Files.createTempFile(destDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        var leftIter = left.segments.iterator(FullPreindexDocuments.RECORD_SIZE_LONGS);
        var rightIter = right.segments.iterator(FullPreindexDocuments.RECORD_SIZE_LONGS);

        try (var wordsWriter = LongArrayFileWriter.create(wordsFile);
             var countsWriter = LongArrayFileWriter.create(countsFile);
             var docsWriter = LongArrayFileWriter.create(docsFile))
        {
            var plan = new MergePlan(wordsWriter, countsWriter, docsWriter);

            boolean leftHasMore = leftIter.next();
            boolean rightHasMore = rightIter.next();

            while (leftHasMore && rightHasMore) {
                if (leftIter.wordId < rightIter.wordId) {
                    plan.copySegment(leftIter, left.documents);
                    leftHasMore = leftIter.next();
                }
                else if (rightIter.wordId < leftIter.wordId) {
                    plan.copySegment(rightIter, right.documents);
                    rightHasMore = rightIter.next();
                }
                else {
                    // both inputs have documents for the current word
                    plan.mergeSegments(leftIter, rightIter, left.documents, right.documents);

                    leftHasMore = leftIter.next();
                    rightHasMore = rightIter.next();
                }
            }

            while (leftHasMore) {
                plan.copySegment(leftIter, left.documents);
                leftHasMore = leftIter.next();
            }

            while (rightHasMore) {
                plan.copySegment(rightIter, right.documents);
                rightHasMore = rightIter.next();
            }
        }

        return new FullPreindexReference(wordsFile, countsFile, docsFile);
    }

    private record MergePlan(LongArrayFileWriter wordsWriter,
                             LongArrayFileWriter countsWriter,
                             LongArrayFileWriter docsWriter)
    {
        void mergeSegments(FullPreindexWordSegments.SegmentIterator leftIter,
                           FullPreindexWordSegments.SegmentIterator rightIter,
                           FullPreindexDocuments left,
                           FullPreindexDocuments right) throws IOException
        {
            long segSize = mergeArraysN(FullPreindexDocuments.RECORD_SIZE_LONGS,
                    docsWriter,
                    left.documents,
                    right.documents,
                    leftIter.startOffset, leftIter.endOffset,
                    rightIter.startOffset, rightIter.endOffset);

            wordsWriter.put(leftIter.wordId);
            countsWriter.put(segSize / FullPreindexDocuments.RECORD_SIZE_LONGS);
        }

        // Straight copy between segments
        void copySegment(FullPreindexWordSegments.SegmentIterator sourceIter,
                         FullPreindexDocuments srcDocuments) throws IOException
        {
            docsWriter.put(srcDocuments.documents, sourceIter.startOffset, sourceIter.endOffset);

            wordsWriter.put(sourceIter.wordId);
            countsWriter.put(sourceIter.size() / FullPreindexDocuments.RECORD_SIZE_LONGS);
        }
    }

}
