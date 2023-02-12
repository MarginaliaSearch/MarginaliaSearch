package nu.marginalia.util.array.delegate;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.algo.LongArraySearch;
import nu.marginalia.util.array.algo.SortingContext;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.array.functional.LongBinaryIOOperation;
import nu.marginalia.util.array.functional.LongIOTransformer;
import nu.marginalia.util.array.functional.LongLongConsumer;
import nu.marginalia.util.array.functional.LongTransformer;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ShiftedLongArray implements LongArray {
    public final long shift;
    public final long size;
    private final LongArray delegate;

    public ShiftedLongArray(long shift, LongArray delegate) {
        this.shift = shift;
        this.size = delegate.size() - shift;
        this.delegate = delegate;
    }

    public ShiftedLongArray(long start, long end, LongArray delegate) {
        this.shift = start;
        this.size = end - start;
        this.delegate = delegate;
    }


    @Override
    public long get(long pos) {
        return delegate.get(pos+shift);
    }

    @Override
    public void set(long pos, long value) {
        delegate.set(pos+shift, value);
    }

    @Override
    public void set(long start, long end, LongBuffer buffer, int bufferStart) {
        delegate.set(shift + start, shift + end, buffer, bufferStart);
    }

    @Override
    public void get(long start, long end, LongBuffer buffer, int bufferStart) {
        delegate.get(shift + start, shift + end, buffer, bufferStart);
    }

    @Override
    public void get(long start, LongBuffer buffer) {
        delegate.get(shift + start, buffer);
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        delegate.get(shift+start, shift+end, buffer);
    }

    @Override
    public long getAndIncrement(long pos) {
        return delegate.getAndIncrement(shift + pos);
    }

    @Override
    public void fill(long start, long end, long val) {
        delegate.fill(start + shift, end + shift, val);
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void write(Path file) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShiftedLongArray shifted(long offset) {
        return new ShiftedLongArray(shift+offset, delegate);
    }

    @Override
    public ShiftedLongArray range(long start, long end) {
        return new ShiftedLongArray(shift + start,  shift+end, delegate);
    }

    public long[] toArray() {
        long[] ret = new long[(int) size];
        for (int i = 0; i < size; i++) {
            ret[i] = delegate.get(shift + i);
        }
        return ret;
    }

    public boolean isSorted() {
        return isSorted(0, size);
    }

    public boolean isSortedN(int sz) {
        return isSortedN(sz, 0, size);
    }

    public boolean isSorted(long start, long end) {
        return delegate.isSorted(shift + start, shift + end);
    }

    public boolean isSortedN(int sz, long start, long end) {
        return delegate.isSortedN(sz, shift + start, shift + end);
    }

    public void sortLargeSpanN(SortingContext ctx, int sz, long start, long end) throws IOException {
        delegate.sortLargeSpanN(ctx, sz, start, end);
    }

    public void sortLargeSpan(SortingContext ctx, long start, long end) throws IOException {
        delegate.sortLargeSpan(ctx, start, end);
    }

    public long searchN(int sz, long key) {
        if (size < 128) {
            return linearSearchN(sz, key);
        }
        else {
            return binarySearchN(sz, key);
        }
    }

    public long search(long key) {
        if (size < 128) {
            return linearSearch(key);
        }
        else {
            return binarySearch(key);
        }
    }

    public long linearSearch(long key) {
        return linearSearch(key, 0, size);
    }

    public long binarySearch(long key) {
        return binarySearch(key, 0, size);
    }

    public long binarySearchN(int sz, long key) {
        return binarySearchN(sz, key, 0, size);
    }

    public long linearSearchN(int sz, long key) {
        return linearSearchN(sz, key, 0, size);
    }

    public void retain(LongQueryBuffer buffer, long boundary) {
        retain(buffer, boundary, 0, size);
    }
    public void retainN(LongQueryBuffer buffer, int sz, long boundary) {
        if (sz == 1)
            retain(buffer, boundary, 0, size);
        else
            retainN(buffer, sz, boundary, 0, size);
    }

    public void reject(LongQueryBuffer buffer, long boundary) {
        reject(buffer, boundary, 0, size);
    }

    public void rejectN(LongQueryBuffer buffer, int sz, long boundary) {
        if (sz == 1)
            reject(buffer, boundary, 0, size);
        else
            rejectN(buffer, sz, boundary, 0, size);

    }

    @Override
    public long linearSearch(long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.linearSearch(key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long linearSearchN(int sz, long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.linearSearch(key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long binarySearch(long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearch(key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long binarySearchN(int sz, long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearchN(sz, key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long binarySearchUpperBound(long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearchUpperBound(key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long linearSearchUpperBound(long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.linearSearchUpperBound(key, fromIndex + shift, toIndex+shift));
    }
    @Override
    public long binarySearchUpperBoundN(int sz, long key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearchUpperBoundN(sz, key, fromIndex + shift, toIndex+shift));
    }
    private long translateSearchResult(long delegatedIdx) {
        long ret;

        if (delegatedIdx >= 0) ret = delegatedIdx - shift;
        else ret = LongArraySearch.encodeSearchMiss(Math.max(0, LongArraySearch.decodeSearchMiss(delegatedIdx) - shift));

        return ret;
    }

    public void retain(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        delegate.retain(buffer, boundary, searchStart + shift, searchEnd + shift);
    }
    public void retainN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {
        delegate.retainN(buffer, sz, boundary, searchStart + shift, searchEnd + shift);
    }
    public void reject(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        delegate.reject(buffer, boundary, searchStart + shift, searchEnd + shift);
    }
    public void rejectN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {
        delegate.rejectN(buffer, sz, boundary, searchStart + shift, searchEnd + shift);
    }

    @Override
    public void forEach(long start, long end, LongLongConsumer consumer) {
        delegate.forEach(start + shift, end+shift, (pos, old) -> consumer.accept(pos-shift, old));
    }

    @Override
    public void transformEach(long start, long end, LongTransformer transformer) {
        delegate.transformEach(start + shift, end+shift, (pos, old) -> transformer.transform(pos-shift, old));
    }

    @Override
    public void transformEachIO(long start, long end, LongIOTransformer transformer) throws IOException {
        delegate.transformEachIO(start + shift, end+shift, (pos, old) -> transformer.transform(pos-shift, old));
    }

    @Override
    public long foldIO(long zero, long start, long end, LongBinaryIOOperation operator) throws IOException {
        return delegate.foldIO(zero, start + shift, end+shift, operator);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {
        delegate.transferFrom(source, sourceStart, shift + arrayStart, shift + arrayEnd);
    }

    @Override
    public void force() {
        delegate.force();
    }

    @Override
    public void advice(NativeIO.Advice advice) throws IOException {
        delegate.advice(advice, shift, shift + size());
    }

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {
        delegate.advice(advice, start + shift, end + shift);
    }

}
