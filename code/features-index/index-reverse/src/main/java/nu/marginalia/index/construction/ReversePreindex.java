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

public class ReversePreindex {
    public final ReversePreindexWordSegments segments;
    public final ReversePreindexDocuments documents;

    private static final Logger logger = LoggerFactory.getLogger(ReversePreindex.class);

    public ReversePreindex(ReversePreindexWordSegments segments, ReversePreindexDocuments documents) {
        this.segments = segments;
        this.documents = documents;
    }

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
    public static ReversePreindex constructPreindex(IndexJournalReader reader,
                                                    DocIdRewriter docIdRewriter,
                                                    Path tempDir,
                                                    Path destDir) throws IOException
    {
        Path segmentWordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path segmentCountsFile = Files.createTempFile(destDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        SortingContext ctx = new SortingContext(tempDir, 1<<31);
        logger.info("Segmenting");
        var segments = ReversePreindexWordSegments.construct(reader, ctx, segmentWordsFile, segmentCountsFile);
        logger.info("Mapping docs");
        var docs = ReversePreindexDocuments.construct(docsFile, reader, docIdRewriter, ctx, segments);
        logger.info("Done");
        return new ReversePreindex(segments, docs);
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
    public static ReversePreindex merge(Path destDir,
                                 ReversePreindex left,
                                 ReversePreindex right) throws IOException {

        ReversePreindexWordSegments mergingSegment = createMergedSegmentWordFile(destDir,
                left.segments,
                right.segments);

        var mergingIter = mergingSegment.constructionIterator(2);
        var leftIter = left.segments.iterator(2);
        var rightIter = right.segments.iterator(2);

        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        LongArray mergedDocuments = LongArray.mmapForWriting(docsFile, 8 * (left.documents.size() + right.documents.size()));

        leftIter.next();
        rightIter.next();

        FileChannel leftChannel = left.documents.createDocumentsFileChannel();
        FileChannel rightChannel = right.documents.createDocumentsFileChannel();

        while (mergingIter.canPutMore()
                && leftIter.isPositionBeforeEnd()
                && rightIter.isPositionBeforeEnd())
        {
            if (leftIter.wordId == mergingIter.wordId
            && rightIter.wordId == mergingIter.wordId) {
                mergeSegments(leftIter,
                        rightIter,
                        left.documents,
                        right.documents,
                        mergedDocuments,
                        mergingIter);
            }
            else if (leftIter.wordId == mergingIter.wordId) {
                if (!copySegment(leftIter, mergedDocuments, leftChannel, mergingIter))
                    break;
            }
            else if (rightIter.wordId == mergingIter.wordId) {
                if (!copySegment(rightIter, mergedDocuments, rightChannel, mergingIter))
                    break;
            }
            else {
                assert false : "This should never happen";
            }
        }

        if (leftIter.isPositionBeforeEnd()) {
            while (copySegment(leftIter, mergedDocuments, leftChannel, mergingIter));

        }
        if (rightIter.isPositionBeforeEnd()) {
            while (copySegment(rightIter, mergedDocuments, rightChannel, mergingIter));
        }

        assert !leftIter.isPositionBeforeEnd() : "Left has more to go";
        assert !rightIter.isPositionBeforeEnd() : "Right has more to go";
        assert !mergingIter.canPutMore() : "Source iters ran dry before merging iter";

        // We may have overestimated the size of the merged docs size in the case there were
        // duplicates in the data, so we need to shrink it to the actual size we wrote.

        mergedDocuments = shrinkMergedDocuments(mergedDocuments, docsFile, 2 * mergingSegment.totalSize());

        mergingSegment.force();

        return new ReversePreindex(
                mergingSegment,
                new ReversePreindexDocuments(mergedDocuments, docsFile)
        );
    }

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

    private static void mergeSegments(ReversePreindexWordSegments.SegmentIterator leftIter,
                                      ReversePreindexWordSegments.SegmentIterator rightIter,
                                      ReversePreindexDocuments left,
                                      ReversePreindexDocuments right,
                                      LongArray documentsFile,
                                      ReversePreindexWordSegments.SegmentConstructionIterator mergingIter)
    {
        long distinct = countDistinctElementsN(2,
                left.documents,
                right.documents,
                leftIter.startOffset, leftIter.endOffset,
                rightIter.startOffset, rightIter.endOffset);

        mergeArrays2(documentsFile,
                left.documents,
                right.documents,
                mergingIter.startOffset,
                mergingIter.startOffset + 2*distinct,
                leftIter.startOffset, leftIter.endOffset,
                rightIter.startOffset, rightIter.endOffset);

        mergingIter.putNext(distinct);
        leftIter.next();
        rightIter.next();
    }

    private static boolean copySegment(ReversePreindexWordSegments.SegmentIterator sourceIter,
                                    LongArray documentsFile,
                                    FileChannel leftChannel,
                                    ReversePreindexWordSegments.SegmentConstructionIterator mergingIter) throws IOException {

        long size = sourceIter.endOffset - sourceIter.startOffset;
        long start = mergingIter.startOffset;
        long end = start + size;

        documentsFile.transferFrom(leftChannel,
                sourceIter.startOffset,
                mergingIter.startOffset,
                end);

        boolean putNext = mergingIter.putNext(size / 2);
        boolean iterNext = sourceIter.next();

        if (!putNext) {
            assert !iterNext: "Source iterator ran out before dest iterator?!";
        }

        return iterNext;

    }


}
