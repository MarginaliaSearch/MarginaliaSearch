package nu.marginalia.index.construction.prio;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.index.ReverseIndexParameters;
import nu.marginalia.index.construction.CountToOffsetTransformer;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.journal.IndexJournalPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.array.algo.TwoArrayOperations.countDistinctElements;
import static nu.marginalia.array.algo.TwoArrayOperations.mergeArrays;

/** Contains the data that would go into a reverse index,
 * that is, a mapping from words to documents, minus the actual
 * index structure that makes the data quick to access while
 * searching.
 * <p>
 * Two preindexes can be merged into a third preindex containing
 * the union of their data.  This operation requires no additional
 * RAM.
 */
public class PrioPreindex {
    final PrioPreindexWordSegments segments;
    final PrioPreindexDocuments documents;

    private static final Logger logger = LoggerFactory.getLogger(PrioPreindex.class);

    public PrioPreindex(PrioPreindexWordSegments segments, PrioPreindexDocuments documents) {
        this.segments = segments;
        this.documents = documents;
    }

    /** Constructs a new preindex with the data associated with reader.  The backing files
     * will have randomly assigned names.
     */
    public static PrioPreindex constructPreindex(IndexJournalPage indexJournalPage,
                                                 DocIdRewriter docIdRewriter,
                                                 Path workDir) throws IOException
    {
        Path segmentWordsFile = Files.createTempFile(workDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(workDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(workDir, "docs", ".dat");

        var segments = PrioPreindexWordSegments.construct(indexJournalPage, segmentWordsFile, segmentCountsFile);
        var docs = PrioPreindexDocuments.construct(docsFile, workDir, indexJournalPage, docIdRewriter, segments);
        return new PrioPreindex(segments, docs);
    }

    /**  Close the associated memory mapped areas and return
     * a dehydrated page of this object that can be re-opened
     * later.
     */
    public PrioPreindexReference closeToReference() {
        try {
            return new PrioPreindexReference(segments, documents);
        }
        finally {
            segments.force();
            documents.force();
            segments.close();
            documents.close();
        }
    }

    /** Transform the preindex into a reverse index */
    public void finalizeIndex(Path outputFileDocs, Path outputFileWords) throws IOException {
        var offsets = segments.counts;

        Files.deleteIfExists(outputFileDocs);
        Files.deleteIfExists(outputFileWords);

        // Estimate the size of the docs index data
        offsets.transformEach(0, offsets.size(), new CountToOffsetTransformer(1));

        // Write the docs file
        try (var intermediateDocChannel = documents.createDocumentsFileChannel();
             var destFileChannel = (FileChannel) Files.newByteChannel(outputFileDocs, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var transformer = new PrioDocIdsTransformer(destFileChannel, intermediateDocChannel)
        ) {
            offsets.transformEachIO(0, offsets.size(), transformer);
        }

        LongArray wordIds = segments.wordIds;

        if (offsets.size() != wordIds.size())
            throw new IllegalStateException("Offsets and word-ids of different size");
        if (offsets.size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("offsets.size() too big!");
        }

        // Estimate the size of the words index data
        long wordsSize = ReverseIndexParameters.wordsBTreeContext.calculateSize((int) offsets.size());

        // Construct the tree
        LongArray wordsArray = LongArrayFactory.mmapForWritingConfined(outputFileWords, wordsSize);

        new BTreeWriter(wordsArray, ReverseIndexParameters.wordsBTreeContext)
            .write(0, (int) offsets.size(), mapRegion -> {
            for (long i = 0; i < offsets.size(); i++) {
                mapRegion.set(2*i, wordIds.get(i));
                mapRegion.set(2*i + 1, offsets.get(i));
            }
        });

        wordsArray.force();
        wordsArray.close();
    }

    /** Delete all files associated with this pre-index */
    public void delete() throws IOException {
        segments.delete();
        documents.delete();
    }

    public static PrioPreindex merge(Path destDir,
                                     PrioPreindex left,
                                     PrioPreindex right) throws IOException {

        PrioPreindexWordSegments mergingSegment =
                createMergedSegmentWordFile(destDir, left.segments, right.segments);

        var mergingIter = mergingSegment.constructionIterator(1);
        var leftIter = left.segments.iterator(1);
        var rightIter = right.segments.iterator(1);

        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        LongArray mergedDocuments = LongArrayFactory.mmapForWritingConfined(docsFile, left.documents.size() + right.documents.size());

        leftIter.next();
        rightIter.next();

        while (mergingIter.canPutMore()
                && leftIter.isPositionBeforeEnd()
                && rightIter.isPositionBeforeEnd())
        {
            final long currentWord = mergingIter.wordId;

            if (leftIter.wordId == currentWord && rightIter.wordId == currentWord)
            {
                // both inputs have documents for the current word
                mergeSegments(leftIter, rightIter,
                        left.documents, right.documents,
                        mergedDocuments, mergingIter);
            }
            else if (leftIter.wordId == currentWord) {
                if (!copySegment(leftIter, left.documents,  mergingIter, mergedDocuments))
                    break;
            }
            else if (rightIter.wordId == currentWord) {
                if (!copySegment(rightIter, right.documents,  mergingIter, mergedDocuments))
                    break;
            }
            else assert false : "This should never happen"; // the helvetica scenario
        }

        if (leftIter.isPositionBeforeEnd()) {
            while (copySegment(leftIter, left.documents,  mergingIter, mergedDocuments));
        }

        if (rightIter.isPositionBeforeEnd()) {
            while (copySegment(rightIter, right.documents,  mergingIter, mergedDocuments));
        }


        if (leftIter.isPositionBeforeEnd())
            throw new IllegalStateException("Left has more to go");
        if (rightIter.isPositionBeforeEnd())
            throw new IllegalStateException("Right has more to go");
        if (mergingIter.canPutMore())
            throw new IllegalStateException("Source iters ran dry before merging iter");


        mergingSegment.force();

        // We may have overestimated the size of the merged docs size in the case there were
        // duplicates in the data, so we need to shrink it to the actual size we wrote.

        mergedDocuments = shrinkMergedDocuments(mergedDocuments,
                docsFile, mergingSegment.totalSize());

        return new PrioPreindex(
                mergingSegment,
                new PrioPreindexDocuments(mergedDocuments, docsFile)
        );
    }

    /** Create a segment word file with each word from both inputs, with zero counts for all the data.
     * This is an intermediate product in merging.
     */
    static PrioPreindexWordSegments createMergedSegmentWordFile(Path destDir,
                                                                PrioPreindexWordSegments left,
                                                                PrioPreindexWordSegments right) throws IOException {
        Path segmentWordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(destDir, "segment_counts", ".dat");

        // We need total size to request a direct LongArray range.  Seems slower, but is faster.
        // ... see LongArray.directRangeIfPossible(long start, long end)
        long segmentsSize = countDistinctElements(left.wordIds, right.wordIds,
                0,  left.wordIds.size(),
                0,  right.wordIds.size());

        LongArray wordIdsFile = LongArrayFactory.mmapForWritingConfined(segmentWordsFile, segmentsSize);

        mergeArrays(wordIdsFile, left.wordIds, right.wordIds,
                0,
                0, left.wordIds.size(),
                0, right.wordIds.size());

        LongArray counts = LongArrayFactory.mmapForWritingConfined(segmentCountsFile, segmentsSize);

        return new PrioPreindexWordSegments(wordIdsFile, counts, segmentWordsFile, segmentCountsFile);
    }

    /** It's possible we overestimated the necessary size of the documents file,
     * this will permit us to shrink it down to the smallest necessary size.
     */
    private static LongArray shrinkMergedDocuments(LongArray mergedDocuments, Path docsFile, long sizeLongs) throws IOException {

        mergedDocuments.force();

        long beforeSize = mergedDocuments.size();
        long afterSize = sizeLongs;
        if (beforeSize != afterSize) {
            mergedDocuments.close();
            try (var bc = Files.newByteChannel(docsFile, StandardOpenOption.WRITE)) {
                bc.truncate(sizeLongs * 8);
            }

            logger.info("Shrunk {} from {}b to {}b", docsFile, beforeSize, afterSize);
            mergedDocuments = LongArrayFactory.mmapForWritingConfined(docsFile, sizeLongs);
        }

        return mergedDocuments;
    }

    /** Merge contents of the segments indicated by leftIter and rightIter into the destionation
     * segment, and advance the construction iterator with the appropriate size.
     */
    private static void mergeSegments(PrioPreindexWordSegments.SegmentIterator leftIter,
                                      PrioPreindexWordSegments.SegmentIterator rightIter,
                                      PrioPreindexDocuments left,
                                      PrioPreindexDocuments right,
                                      LongArray dest,
                                      PrioPreindexWordSegments.SegmentConstructionIterator destIter)
    {
        long segSize = mergeArrays(dest,
                left.documents,
                right.documents,
                destIter.startOffset,
                leftIter.startOffset, leftIter.endOffset,
                rightIter.startOffset, rightIter.endOffset);

        destIter.putNext(segSize);
        leftIter.next();
        rightIter.next();
    }

    /** Copy the data from the source segment at the position and length indicated by sourceIter,
     * into the destination segment, and advance the construction iterator.
     */
    /** Copy the data from the source segment at the position and length indicated by sourceIter,
     * into the destination segment, and advance the construction iterator.
     */
    private static boolean copySegment(PrioPreindexWordSegments.SegmentIterator sourceIter,
                                       PrioPreindexDocuments srcDocuments,
                                       PrioPreindexWordSegments.SegmentConstructionIterator mergingIter,
                                       LongArray dest) throws IOException {

        long size = sourceIter.endOffset - sourceIter.startOffset;
        long start = mergingIter.startOffset;
        long end = start + size;

        dest.transferFrom(srcDocuments.documents,
                sourceIter.startOffset,
                mergingIter.startOffset,
                end);

        boolean putNext = mergingIter.putNext(size / 2);
        boolean iterNext = sourceIter.next();

        if (!putNext && iterNext)
            throw new IllegalStateException("Source iterator ran out before dest iterator?!");

        return iterNext;
    }


}
