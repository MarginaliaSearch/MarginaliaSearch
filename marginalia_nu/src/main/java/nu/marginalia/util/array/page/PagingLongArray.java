package nu.marginalia.util.array.page;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.algo.SortingContext;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.array.delegate.ReferenceImplLongArrayDelegate;
import nu.marginalia.util.array.functional.LongBinaryIOOperation;
import nu.marginalia.util.array.functional.LongIOTransformer;
import nu.marginalia.util.array.functional.LongLongConsumer;
import nu.marginalia.util.array.functional.LongTransformer;
import nu.marginalia.util.array.functor.LongIOFolder;
import nu.marginalia.util.array.scheme.ArrayPartitioningScheme;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PagingLongArray extends AbstractPagingArray<LongArrayPage, LongBuffer> implements LongArray {
    private final ReferenceImplLongArrayDelegate defaults;

    private PagingLongArray(ArrayPartitioningScheme partitioningScheme, LongArrayPage[] pages, long size) {
        super(partitioningScheme, pages, size);
        defaults = new ReferenceImplLongArrayDelegate(this);
    }

    public static LongArray newOnHeap(ArrayPartitioningScheme partitioningScheme, long cardinality) {
        return newPartitionedOnHeap(partitioningScheme, cardinality);
    }

    public static LongArray newPartitionedOnHeap(ArrayPartitioningScheme partitioningScheme, long cardinality) {
        LongArrayPage[] pages = new LongArrayPage[partitioningScheme.getPartitions(cardinality)];

        for (int i = 0; i < pages.length; i++) {
            pages[i] = LongArrayPage.onHeap(partitioningScheme.getRequiredPageSize(i, cardinality));
        }

        return new PagingLongArray(partitioningScheme, pages, cardinality);
    }

    public static PagingLongArray mapFileReadOnly(ArrayPartitioningScheme partitioningScheme, Path file)
            throws IOException
    {
        long sizeBytes = Files.size(file);
        assert sizeBytes % WORD_SIZE == 0;

        long size = sizeBytes / WORD_SIZE;

        LongArrayPage[] pages = new LongArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = LongArrayPage.fromMmapReadOnly(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingLongArray(partitioningScheme, pages, size);
    }

    public static PagingLongArray mapFileReadWrite(ArrayPartitioningScheme partitioningScheme, Path file)
            throws IOException
    {
        long sizeBytes = Files.size(file);
        assert sizeBytes % WORD_SIZE == 0;

        long size = sizeBytes / WORD_SIZE;

        LongArrayPage[] pages = new LongArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = LongArrayPage.fromMmapReadWrite(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingLongArray(partitioningScheme, pages, size);
    }

    public static PagingLongArray mapFileReadWrite(ArrayPartitioningScheme partitioningScheme, Path file, long size)
            throws IOException
    {
        LongArrayPage[] pages = new LongArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = LongArrayPage.fromMmapReadWrite(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingLongArray(partitioningScheme, pages, size);
    }

    @Override
    public long get(long pos) {
        int page = partitioningScheme.getPage(pos);
        int offset = partitioningScheme.getOffset(pos);

        try {
            return pages[page].get(partitioningScheme.getOffset(pos));
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("Index out of bounds for " + pos + " => (" + page + ":" + offset + ")");
        }
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            pages[partitioningScheme.getPage(start)].get(sOff, eOff, buffer);
        }
        else {
            defaults.get(start, end, buffer);
        }
    }

    @Override
    public void set(long pos, long value) {
        int page = partitioningScheme.getPage(pos);
        int offset = partitioningScheme.getOffset(pos);
        try {
            pages[page].set(offset, value);
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("Index out of bounds for " + pos + " => (" + page + ":" + offset + ")");
        }
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void increment(long pos) {
        int page = partitioningScheme.getPage(pos);
        int offset = partitioningScheme.getOffset(pos);

        try {
            pages[page].increment(partitioningScheme.getOffset(pos));
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("Index out of bounds for " + pos + " => (" + page + ":" + offset + ")");
        }
    }

    @Override
    public void forEach(long start, long end, LongLongConsumer transformer) {
        delegateToEachPage(start, end, (page, s, e) -> page.forEach(s, e, transformer));
    }

    @Override
    public void fill(long fromIndex, long toIndex, long value) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {

            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            pages[partitioningScheme.getPage(fromIndex)].fill(sOff, eOff, value);
        }
        else {
            delegateToEachPage(fromIndex, toIndex, (page, s, e) -> page.fill(s, e, value));
        }
    }

    @Override
    public void transformEach(long start, long end, LongTransformer transformer) {
        delegateToEachPage(start, end, (page, s, e) -> page.transformEach(s, e, transformer));
    }

    @Override
    public void transformEachIO(long start, long end, LongIOTransformer transformer) throws IOException {
        delegateToEachPageIO(start, end, (page, s, e) -> page.transformEachIO(s, e, transformer));
    }

    @Override
    public long foldIO(long zero, long start, long end, LongBinaryIOOperation operator) throws IOException {
        var folder = new LongIOFolder(zero, operator);

        delegateToEachPageIO(start, end, folder);

        return folder.acc;
    }

    @Override
    public long linearSearch(long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {

            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].linearSearch(key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.linearSearch(key, fromIndex, toIndex);
        }
    }

    @Override
    public long linearSearchN(int sz, long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].linearSearchN(sz, key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.linearSearchN(sz, key, fromIndex, toIndex);
        }
    }

    @Override
    public long binarySearch(long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].binarySearch(key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.binarySearch(key, fromIndex, toIndex);
        }
    }
    @Override
    public long binarySearchN(int sz, long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].binarySearchN(sz, key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.binarySearchN(sz, key, fromIndex, toIndex);
        }
    }
    @Override
    public long binarySearchUpperBound(long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].binarySearchUpperBound(key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.binarySearchUpperBound(key, fromIndex, toIndex);
        }
    }

    @Override
    public long linearSearchUpperBound(long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].linearSearchUpperBound(key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.linearSearchUpperBound(key, fromIndex, toIndex);
        }
    }
    @Override
    public long binarySearchUpperBoundN(int sz, long key, long fromIndex, long toIndex) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {
            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            long ret = pages[partitioningScheme.getPage(fromIndex)].binarySearchUpperBoundN(sz, key, sOff, eOff);

            return translateSearchResultsFromPage(fromIndex, ret);
        }
        else {
            return defaults.binarySearchUpperBoundN(sz, key, fromIndex, toIndex);
        }
    }

    @Override
    public void retain(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        if (partitioningScheme.isSamePage(searchStart, searchEnd)) {
            int sOff = partitioningScheme.getOffset(searchStart);
            int eOff = partitioningScheme.getEndOffset(searchStart, searchEnd);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(searchStart)].retain(buffer, boundary, sOff, eOff);
            }
        }
        else {
            defaults.retain(buffer, boundary, searchStart, searchEnd);
        }
    }

    @Override
    public void retainN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {
        if (partitioningScheme.isSamePage(searchStart, searchEnd)) {
            int sOff = partitioningScheme.getOffset(searchStart);
            int eOff = partitioningScheme.getEndOffset(searchStart, searchEnd);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(searchStart)].retainN(buffer, sz, boundary, sOff, eOff);
            }
        }
        else {
            defaults.retainN(buffer, sz, boundary, searchStart, searchEnd);
        }
    }

    @Override
    public void reject(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        if (partitioningScheme.isSamePage(searchStart, searchEnd)) {
            int sOff = partitioningScheme.getOffset(searchStart);
            int eOff = partitioningScheme.getEndOffset(searchStart, searchEnd);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(searchStart)].reject(buffer, boundary, sOff, eOff);
            }
        }
        else {
            defaults.reject(buffer, boundary, searchStart, searchEnd);
        }
    }

    @Override
    public void rejectN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {
        if (partitioningScheme.isSamePage(searchStart, searchEnd)) {
            int sOff = partitioningScheme.getOffset(searchStart);
            int eOff = partitioningScheme.getEndOffset(searchStart, searchEnd);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(searchStart)].rejectN(buffer, sz, boundary, sOff, eOff);
            }
        }
        else {
            defaults.rejectN(buffer, sz, boundary, searchStart, searchEnd);
        }
    }

    @Override
    public void insertionSort(long start, long end) {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].insertionSort(sOff, eOff);
            }
        }
        else {
            defaults.insertionSort(start, end);
        }
    }

    @Override
    public void insertionSortN(int sz, long start, long end) {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].insertionSortN(sz, sOff, eOff);
            }
        }
        else {
            defaults.insertionSortN(sz, start, end);
        }
    }

    @Override
    public void quickSort(long start, long end) {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].quickSort(sOff, eOff);
            }
        }
        else {
            defaults.quickSort(start, end);
        }
    }

    @Override
    public void quickSortN(int sz, long start, long end) {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].quickSortN(sz, sOff, eOff);
            }
        }
        else {
            defaults.quickSortN(sz, start, end);
        }
    }

    @Override
    public void mergeSort(long start, long end, Path tempDir) throws IOException {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].mergeSort(sOff, eOff, tempDir);
            }
        }
        else {
            defaults.mergeSort(start, end, tempDir);
        }
    }


    @Override
    public void mergeSortN(int sz, long start, long end, Path tempDir) throws IOException {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].mergeSortN(sz, sOff, eOff, tempDir);
            }
        }
        else {
            defaults.mergeSortN(sz, start, end, tempDir);
        }
    }
    public void sortLargeSpanN(SortingContext ctx, int sz, long start, long end) throws IOException {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].sortLargeSpanN(ctx, sz, sOff, eOff);
            }
        }
        else {
            defaults.sortLargeSpanN(ctx, sz, start, end);
        }
    }

    public void sortLargeSpan(SortingContext ctx, long start, long end) throws IOException {
        if (partitioningScheme.isSamePage(start, end)) {
            int sOff = partitioningScheme.getOffset(start);
            int eOff = partitioningScheme.getEndOffset(start, end);

            if (eOff > sOff) {
                pages[partitioningScheme.getPage(start)].sortLargeSpan(ctx, sOff, eOff);
            }
        }
        else {
            defaults.sortLargeSpan(ctx, start, end);
        }
    }

    public void write(Path fileName) throws IOException {
        try (var channel = (FileChannel) Files.newByteChannel(fileName, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < pages.length; i++) {
                pages[i].write(channel);
            }
            channel.force(false);
        }
    }

    public long getSize() {
        if (size < 0) {
            throw new UnsupportedOperationException();
        }
        return size;
    }

    @Override
    public void force() {
        for (var page : pages) {
            page.force();
        }
    }

    @Override
    public void advice(NativeIO.Advice advice) throws IOException {
        for (var page : pages) {
            page.advice(advice);
        }
    }

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {
        delegateToEachPageIO(start, end, (a,s,e) -> a.advice(advice, s, e));
    }


    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {
        assert arrayEnd >= arrayStart;

        int page = partitioningScheme.getPage(arrayStart);

        long endPos;

        for (long pos = arrayStart; pos < arrayEnd; pos = endPos) {
            endPos = partitioningScheme.getPageEnd(pos, arrayEnd);

            int sOff = partitioningScheme.getOffset(pos);
            int eOff = partitioningScheme.getEndOffset(pos, endPos);

            pages[page++].transferFrom(source, sourceStart, sOff, eOff);

            sourceStart+=(endPos - pos);
        }
    }

}
