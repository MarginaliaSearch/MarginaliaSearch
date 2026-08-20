package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.LongArrayFileWriter;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.index.config.ReverseIndexParameters;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
public class PrioPreindex {
    final PrioPreindexWordSegments segments;
    final PrioPreindexDocuments documents;

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
            documents.force();
            segments.close();
            documents.close();
        }
    }

    /** Transform the preindex into a reverse index */
    public void finalizeIndex(Path outputFileDocs, Path outputFileWords) throws IOException {
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
        try (var intermediateDocChannel = documents.createDocumentsFileChannel();
             var destFileChannel = (FileChannel) Files.newByteChannel(outputFileDocs, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var transformer = new PrioDocIdsTransformer(destFileChannel, intermediateDocChannel))
        {
            destFileChannel.position(destFileChannel.size());

            new BTreeWriter(wordsArray, ReverseIndexParameters.wordsBTreeContext)
                .write(0, (int) counts.size(), mapRegion -> {
                    long segmentEnd = 0;
                    for (long i = 0; i < counts.size(); i++) {
                        segmentEnd += counts.get(i);

                        mapRegion.set(2*i, wordIds.get(i));
                        mapRegion.set(2*i + 1, transformer.transform(i, segmentEnd));
                    }
                });
        }

        wordsArray.force();
        wordsArray.close();
    }

    /** Delete all files associated with this pre-index */
    public void delete() throws IOException {
        segments.delete();
        documents.delete();
    }

    public static PrioPreindexReference merge(Path destDir,
                                              PrioPreindex left,
                                              PrioPreindex right) throws IOException {

        Path wordsFile = Files.createTempFile(destDir, "segment_words", ".dat");
        Path countsFile = Files.createTempFile(destDir, "segment_counts", ".dat");
        Path docsFile = Files.createTempFile(destDir, "docs", ".dat");

        var leftIter = left.segments.iterator(1);
        var rightIter = right.segments.iterator(1);

        try (var wordsWriter = LongArrayFileWriter.create(wordsFile);
             var countsWriter = LongArrayFileWriter.create(countsFile);
             var docsWriter = LongArrayFileWriter.create(docsFile))
        {
            var dest = new MergeDestination(wordsWriter, countsWriter, docsWriter);

            boolean leftHasMore = leftIter.next();
            boolean rightHasMore = rightIter.next();

            while (leftHasMore && rightHasMore) {
                if (leftIter.wordId < rightIter.wordId) {
                    dest.copySegment(leftIter, left.documents);
                    leftHasMore = leftIter.next();
                }
                else if (rightIter.wordId < leftIter.wordId) {
                    dest.copySegment(rightIter, right.documents);
                    rightHasMore = rightIter.next();
                }
                else {
                    // both inputs have documents for the current word
                    dest.mergeSegments(leftIter, rightIter, left.documents, right.documents);
                    leftHasMore = leftIter.next();
                    rightHasMore = rightIter.next();
                }
            }

            while (leftHasMore) {
                dest.copySegment(leftIter, left.documents);
                leftHasMore = leftIter.next();
            }

            while (rightHasMore) {
                dest.copySegment(rightIter, right.documents);
                rightHasMore = rightIter.next();
            }
        }

        return new PrioPreindexReference(wordsFile, countsFile, docsFile);
    }

    private record MergeDestination(LongArrayFileWriter wordsWriter,
                                    LongArrayFileWriter countsWriter,
                                    LongArrayFileWriter docsWriter)
    {
        void mergeSegments(PrioPreindexWordSegments.SegmentIterator leftIter,
                           PrioPreindexWordSegments.SegmentIterator rightIter,
                           PrioPreindexDocuments left,
                           PrioPreindexDocuments right) throws IOException
        {
            long segSize = mergeArraysN(1,
                    docsWriter,
                    left.documents,
                    right.documents,
                    leftIter.startOffset, leftIter.endOffset,
                    rightIter.startOffset, rightIter.endOffset);

            wordsWriter.put(leftIter.wordId);
            countsWriter.put(segSize);
        }

        // Straight copy between segments
        void copySegment(PrioPreindexWordSegments.SegmentIterator sourceIter,
                         PrioPreindexDocuments srcDocuments) throws IOException
        {
            docsWriter.put(srcDocuments.documents, sourceIter.startOffset, sourceIter.endOffset);

            wordsWriter.put(sourceIter.wordId);
            countsWriter.put(sourceIter.size());
        }
    }

}
