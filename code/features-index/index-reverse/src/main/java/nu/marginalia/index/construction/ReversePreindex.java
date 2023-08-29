package nu.marginalia.index.construction;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.index.ReverseIndexParameters;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.array.algo.TwoArrayOperations.*;

/** Contains the data that would go into a reverse index,
 * that is, a mapping from words to documents, minus the actual
 * index structure that makes the data quick to access while
 * searching.
 * <p>
 * Two preindexes can be merged into a third preindex containing
 * the union of their data.  This operation requires no additional
 * RAM.
 */
public class ReversePreindex {
    final ReversePreindexWordSegments segments;
    final ReversePreindexDocuments documents;

    private static final Logger logger = LoggerFactory.getLogger(ReversePreindex.class);

    public ReversePreindex(ReversePreindexWordSegments segments, ReversePreindexDocuments documents) {
        this.segments = segments;
        this.documents = documents;
    }

    /** Constructs a new preindex with the data associated with reader.  The backing files
     * will have randomly assigned names.
     */
    public static ReversePreindex constructPreindex(IndexJournalReader reader,
                                                    DocIdRewriter docIdRewriter,
                                                    Path destDir) throws IOException
    {
        Path segmentWordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(destDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        logger.info("Segmenting");
        var segments = ReversePreindexWordSegments.construct(reader, segmentWordsFile, segmentCountsFile);
        logger.info("Mapping docs");
        var docs = ReversePreindexDocuments.construct(docsFile, reader, docIdRewriter, segments);
        logger.info("Done");
        return new ReversePreindex(segments, docs);
    }

    /** Transform the preindex into a reverse index */
    public void finalizeIndex(Path outputFileDocs, Path outputFileWords) throws IOException {
        var offsets = segments.counts;

        Files.deleteIfExists(outputFileDocs);
        Files.deleteIfExists(outputFileWords);

        // Estimate the size of the docs index data
        offsets.transformEach(0, offsets.size(), new CountToOffsetTransformer(2));
        IndexSizeEstimator sizeEstimator = new IndexSizeEstimator(ReverseIndexParameters.docsBTreeContext, 2);
        offsets.fold(0, 0, offsets.size(), sizeEstimator);

        // Write the docs file
        LongArray finalDocs = LongArray.mmapForWriting(outputFileDocs, sizeEstimator.size);
        try (var intermediateDocChannel = documents.createDocumentsFileChannel()) {
            offsets.transformEachIO(0, offsets.size(), new ReverseIndexBTreeTransformer(finalDocs, 2, ReverseIndexParameters.docsBTreeContext, intermediateDocChannel));
            intermediateDocChannel.force(false);
        }

        LongArray wordIds = segments.wordIds;

        assert offsets.size() == wordIds.size() : "Offsets and word-ids of different size";

        // Estimate the size of the words index data
        long wordsSize = ReverseIndexParameters.wordsBTreeContext.calculateSize((int) offsets.size());

        // Construct the tree
        LongArray wordsArray = LongArray.mmapForWriting(outputFileWords, wordsSize);

        new BTreeWriter(wordsArray, ReverseIndexParameters.wordsBTreeContext)
            .write(0, (int) offsets.size(), mapRegion -> {
            for (long i = 0; i < offsets.size(); i++) {
                mapRegion.set(2*i, wordIds.get(i));
                mapRegion.set(2*i + 1, offsets.get(i));
            }
        });

        wordsArray.force();

    }

    /** Delete all files associated with this pre-index */
    public void delete() throws IOException {
        segments.delete();
        documents.delete();
    }

    public static ReversePreindex merge(Path destDir,
                                        ReversePreindex left,
                                        ReversePreindex right) throws IOException {

        ReversePreindexWordSegments mergingSegment =
                createMergedSegmentWordFile(destDir, left.segments, right.segments);

        var mergingIter = mergingSegment.constructionIterator(2);
        var leftIter = left.segments.iterator(2);
        var rightIter = right.segments.iterator(2);

        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        LongArray mergedDocuments = LongArray.mmapForWriting(docsFile, 8 * (left.documents.size() + right.documents.size()));

        leftIter.next();
        rightIter.next();

        try (FileChannel leftChannel = left.documents.createDocumentsFileChannel();
             FileChannel rightChannel = right.documents.createDocumentsFileChannel())
        {

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
                    if (!copySegment(leftIter, mergedDocuments, leftChannel, mergingIter))
                        break;
                }
                else if (rightIter.wordId == currentWord) {
                    if (!copySegment(rightIter, mergedDocuments, rightChannel, mergingIter))
                        break;
                }
                else assert false : "This should never happen"; // the helvetica scenario
            }

            if (leftIter.isPositionBeforeEnd()) {
                while (copySegment(leftIter, mergedDocuments, leftChannel, mergingIter));
            }

            if (rightIter.isPositionBeforeEnd()) {
                while (copySegment(rightIter, mergedDocuments, rightChannel, mergingIter));
            }

        }

        assert !leftIter.isPositionBeforeEnd() : "Left has more to go";
        assert !rightIter.isPositionBeforeEnd() : "Right has more to go";
        assert !mergingIter.canPutMore() : "Source iters ran dry before merging iter";

        // We may have overestimated the size of the merged docs size in the case there were
        // duplicates in the data, so we need to shrink it to the actual size we wrote.

        mergedDocuments = shrinkMergedDocuments(mergedDocuments,
                docsFile, 2 * mergingSegment.totalSize());

        mergingSegment.force();

        return new ReversePreindex(
                mergingSegment,
                new ReversePreindexDocuments(mergedDocuments, docsFile)
        );
    }

    /** Create a segment word file with each word from both inputs, with zero counts for all the data.
     * This is an intermediate product in merging.
     */
    static ReversePreindexWordSegments createMergedSegmentWordFile(Path destDir,
                                                                   ReversePreindexWordSegments left,
                                                                   ReversePreindexWordSegments right) throws IOException {
        Path segmentWordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(destDir, "segment_counts", ".dat");

        long segmentsSize = countDistinctElements(left.wordIds, right.wordIds,
                0,  left.wordIds.size(),
                0,  right.wordIds.size());

        LongArray wordIdsFile = LongArray.mmapForWriting(segmentWordsFile, segmentsSize);

        mergeArrays(wordIdsFile, left.wordIds, right.wordIds,
                0, wordIdsFile.size(),
                0, left.wordIds.size(),
                0, right.wordIds.size());

        LongArray counts = LongArray.mmapForWriting(segmentCountsFile, segmentsSize);

        return new ReversePreindexWordSegments(wordIdsFile, counts, segmentWordsFile, segmentCountsFile);
    }

    /** It's possible we overestimated the necessary size of the documents file,
     * this will permit us to shrink it down to the smallest necessary size.
     */
    private static LongArray shrinkMergedDocuments(LongArray mergedDocuments, Path docsFile, long sizeLongs) throws IOException {

        mergedDocuments.force();

        long beforeSize = mergedDocuments.size();
        try (var bc = Files.newByteChannel(docsFile, StandardOpenOption.WRITE)) {
            bc.truncate(sizeLongs * 8);
        }
        long afterSize = mergedDocuments.size();
        mergedDocuments = LongArray.mmapForWriting(docsFile, sizeLongs);

        if (beforeSize != afterSize) {
            logger.info("Shrunk {} from {}b to {}b", docsFile, beforeSize, afterSize);
        }

        return mergedDocuments;
    }

    /** Merge contents of the segments indicated by leftIter and rightIter into the destionation
     * segment, and advance the construction iterator with the appropriate size.
     */
    private static void mergeSegments(ReversePreindexWordSegments.SegmentIterator leftIter,
                                      ReversePreindexWordSegments.SegmentIterator rightIter,
                                      ReversePreindexDocuments left,
                                      ReversePreindexDocuments right,
                                      LongArray dest,
                                      ReversePreindexWordSegments.SegmentConstructionIterator destIter)
    {
        long distinct = countDistinctElementsN(2,
                left.documents,
                right.documents,
                leftIter.startOffset, leftIter.endOffset,
                rightIter.startOffset, rightIter.endOffset);

        mergeArrays2(dest,
                left.documents,
                right.documents,
                destIter.startOffset,
                destIter.startOffset + 2*distinct,
                leftIter.startOffset, leftIter.endOffset,
                rightIter.startOffset, rightIter.endOffset);

        destIter.putNext(distinct);
        leftIter.next();
        rightIter.next();
    }

    /** Copy the data from the source segment at the position and length indicated by sourceIter,
     * into the destination segment, and advance the construction iterator.
     */
    private static boolean copySegment(ReversePreindexWordSegments.SegmentIterator sourceIter,
                                    LongArray dest,
                                    FileChannel sourceChannel,
                                    ReversePreindexWordSegments.SegmentConstructionIterator mergingIter) throws IOException {

        long size = sourceIter.endOffset - sourceIter.startOffset;
        long start = mergingIter.startOffset;
        long end = start + size;

        dest.transferFrom(sourceChannel,
                sourceIter.startOffset,
                mergingIter.startOffset,
                end);

        boolean putNext = mergingIter.putNext(size / 2);
        boolean iterNext = sourceIter.next();

        assert putNext || !iterNext : "Source iterator ran out before dest iterator?!";

        return iterNext;
    }


}
