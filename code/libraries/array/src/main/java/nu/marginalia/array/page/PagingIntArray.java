package nu.marginalia.array.page;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.IntArray;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.array.buffer.IntQueryBuffer;
import nu.marginalia.array.delegate.ReferenceImplIntArrayDelegate;
import nu.marginalia.array.functional.*;
import nu.marginalia.array.functor.IntFolder;
import nu.marginalia.array.functor.IntIOFolder;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PagingIntArray extends AbstractPagingArray<IntArrayPage, IntBuffer> implements IntArray {
    private final ReferenceImplIntArrayDelegate defaults;

    private PagingIntArray(ArrayPartitioningScheme partitioningScheme,
                           IntArrayPage[] pages,
                           long size) {
        super(partitioningScheme, pages, size);

        defaults = new ReferenceImplIntArrayDelegate(this);
    }

    public static IntArray newOnHeap(ArrayPartitioningScheme partitioningScheme, long cardinality) {
        if (cardinality < MAX_CONTINUOUS_SIZE) {
            return IntArrayPage.onHeap((int) cardinality);
        }

        return newPartitionedOnHeap(partitioningScheme, cardinality);
    }

    public static IntArray newPartitionedOnHeap(ArrayPartitioningScheme partitioningScheme, long cardinality) {

        IntArrayPage[] pages = new IntArrayPage[partitioningScheme.getPartitions(cardinality)];

        for (int i = 0; i < pages.length; i++) {
            pages[i] = IntArrayPage.onHeap(partitioningScheme.getRequiredPageSize(i, cardinality));
        }

        return new PagingIntArray(partitioningScheme, pages, cardinality);
    }

    public static PagingIntArray mapFileReadOnly(ArrayPartitioningScheme partitioningScheme, Path file)
            throws IOException
    {
        long sizeBytes = Files.size(file);
        assert sizeBytes % WORD_SIZE == 0;

        long size = sizeBytes / WORD_SIZE;

        IntArrayPage[] pages = new IntArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = IntArrayPage.fromMmapReadOnly(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingIntArray(partitioningScheme, pages, size);
    }


    public static PagingIntArray mapFileReadWrite(ArrayPartitioningScheme partitioningScheme, Path file)
            throws IOException
    {
        long sizeBytes = Files.size(file);
        assert sizeBytes % LongArrayPage.WORD_SIZE == 0;

        long size = sizeBytes / LongArrayPage.WORD_SIZE;

        IntArrayPage[] pages = new IntArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = IntArrayPage.fromMmapReadWrite(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingIntArray(partitioningScheme, pages, size);
    }

    public static PagingIntArray mapFileReadWrite(ArrayPartitioningScheme partitioningScheme, Path file, long size)
            throws IOException
    {
        IntArrayPage[] pages = new IntArrayPage[partitioningScheme.getPartitions(size)];
        long offset = 0;
        for (int i = 0; i < pages.length; i++) {
            int partitionSize = partitioningScheme.getRequiredPageSize(i, size);
            pages[i] = IntArrayPage.fromMmapReadWrite(file, offset, partitionSize);
            offset += partitionSize;
        }

        return new PagingIntArray(partitioningScheme, pages, size);
    }

    public int get(long pos) {
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
    public int getAndIncrement(long pos) {
        return pages[partitioningScheme.getPage(pos)].getAndIncrement(partitioningScheme.getOffset(pos));
    }

    @Override
    public void get(long start, long end, int[] buffer) {
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
    public void set(long pos, int value) {
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
    public void forEach(long start, long end, LongIntConsumer consumer) {
        delegateToEachPage(start, end, (page, s, e) -> page.forEach(s, e, consumer));
    }

    @Override
    public void fill(long fromIndex, long toIndex, int value) {
        if (partitioningScheme.isSamePage(fromIndex, toIndex)) {

            int sOff = partitioningScheme.getOffset(fromIndex);
            int eOff = partitioningScheme.getEndOffset(fromIndex, toIndex);

            pages[partitioningScheme.getPage(fromIndex)].fill(sOff, eOff, value);
        }
        else if (toIndex >= fromIndex) {
            delegateToEachPage(fromIndex, toIndex, (page, s, e) -> page.fill(s, e, value));
        }
    }

    @Override
    public void transformEach(long start, long end, IntTransformer transformer) {
        delegateToEachPage(start, end, (page, s, e) -> page.transformEach(s, e, transformer));
    }

    @Override
    public void transformEachIO(long start, long end, IntIOTransformer transformer) throws IOException {
        delegateToEachPageIO(start, end, (page, s, e) -> page.transformEachIO(s, e, transformer));
    }

    @Override
    public int foldIO(int zero, long start, long end,  IntBinaryIOOperation operator) throws IOException {
        var folder = new IntIOFolder(zero, operator);

        delegateToEachPageIO(start, end, folder);

        return folder.acc;
    }

    @Override
    public int fold(int zero, long start, long end,  IntBinaryOperation operator) {
        var folder = new IntFolder(zero, operator);

        delegateToEachPage(start, end, folder);

        return folder.acc;
    }

    @Override
    public long linearSearch(int key, long fromIndex, long toIndex) {
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
    public long binarySearch(int key, long fromIndex, long toIndex) {
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
    public long binarySearchUpperBound(int key, long fromIndex, long toIndex) {
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
    public void retain(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
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
    public void reject(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
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
    public ArrayRangeReference<IntArray> directRangeIfPossible(long start, long end) {
        if (partitioningScheme.isSamePage(start, end)) {
            return new ArrayRangeReference<>(
                    pages[partitioningScheme.getPage(start)],
                    partitioningScheme.getOffset(start),
                    partitioningScheme.getEndOffset(start, end));
        }
        else {
            return new ArrayRangeReference<>(this, start, end);
        }
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
