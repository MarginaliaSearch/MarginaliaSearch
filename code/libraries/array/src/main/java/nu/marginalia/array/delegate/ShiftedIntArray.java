package nu.marginalia.array.delegate;

import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.IntArray;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.array.buffer.IntQueryBuffer;
import nu.marginalia.array.functional.*;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ShiftedIntArray implements IntArray {
    public final long shift;
    public final long size;

    private final IntArray delegate;

    public ShiftedIntArray(long shift, IntArray delegate) {
        this.shift = shift;
        this.size = delegate.size() - shift;
        this.delegate = delegate;
    }

    public ShiftedIntArray(long start, long end, IntArray delegate) {
        this.shift = start;
        this.size = end - start;
        this.delegate = delegate;
    }

    @Override
    public int get(long pos) {
        return delegate.get(pos+shift);
    }

    @Override
    public void set(long pos, int value) {
        delegate.set(pos+shift, value);
    }

    @Override
    public void set(long start, long end, IntBuffer buffer, int bufferStart) {
        delegate.set(shift + start, shift + end, buffer, bufferStart);
    }

    @Override
    public void get(long start, long end, IntBuffer buffer, int bufferStart) {
        delegate.get(shift + start, shift + end, buffer, bufferStart);
    }

    @Override
    public void get(long start, IntBuffer buffer) {
        delegate.get(shift + start, buffer);
    }

    @Override
    public void get(long start, long end, int[] buffer) {
        delegate.get(shift+start, shift+end, buffer);
    }

    @Override
    public int getAndIncrement(long pos) {
        return delegate.getAndIncrement(shift + pos);
    }

    @Override
    public void fill(long start, long end, int val) {
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
    public ShiftedIntArray shifted(long offset) {
        return new ShiftedIntArray(shift+offset, delegate);
    }

    @Override
    public ShiftedIntArray range(long start, long end) {
        return new ShiftedIntArray(shift + start,  shift+end, delegate);
    }

    public ArrayRangeReference<IntArray> directRangeIfPossible(long start, long end) {
        return delegate.directRangeIfPossible(shift + start, shift + end);
    }

    public int[] toArray() {
        int[] ret = new int[(int) size];
        for (int i = 0; i < size; i++) {
            ret[i] = delegate.get(shift + i);
        }
        return ret;
    }

    public boolean isSorted() {
        return isSorted(0, size);
    }

    public boolean isSorted(long start, long end) {
        return delegate.isSorted(shift + start, shift + end);
    }


    public void sortLargeSpan(SortingContext ctx, long start, long end) throws IOException {
        delegate.sortLargeSpan(ctx, start, end);
    }


    public long search(int key) {
        if (size < 128) {
            return linearSearch(key);
        }
        else {
            return binarySearch(key);
        }
    }

    public long linearSearch(int key) {
        return linearSearch(key, 0, size);
    }

    public long binarySearch(int key) {
        return binarySearch(key, 0, size);
    }

    public long binarySearchUpperbound(int key) {
        return binarySearchUpperBound(key, 0, size);
    }

    public void retain(IntQueryBuffer buffer, long boundary) {
        retain(buffer, boundary, 0, size);
    }

    public void reject(IntQueryBuffer buffer, long boundary) {
        reject(buffer, boundary, 0, size);
    }

    @Override
    public long linearSearch(int key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.linearSearch(key, fromIndex + shift, toIndex+shift));
    }

    @Override
    public long binarySearch(int key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearch(key, fromIndex + shift, toIndex+shift));
    }

    @Override
    public long binarySearchUpperBound(int key, long fromIndex, long toIndex) {
        return translateSearchResult(delegate.binarySearchUpperBound(key, fromIndex + shift, toIndex+shift));
    }

    private long translateSearchResult(long ret) {
        if (ret > 0) return ret - shift;
        return ret + shift;
    }

    @Override
    public void retain(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        delegate.retain(buffer, boundary, searchStart + shift, searchEnd + shift);
    }

    @Override
    public void reject(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {
        delegate.reject(buffer, boundary, searchStart + shift, searchEnd + shift);
    }

    @Override
    public void forEach(long start, long end, LongIntConsumer consumer) {
        delegate.forEach(start + shift, end+shift, (pos, old) -> consumer.accept(pos-shift, old));
    }

    @Override
    public void transformEach(long start, long end, IntTransformer transformer) {
        delegate.transformEach(start + shift, end+shift, (pos, old) -> transformer.transform(pos-shift, old));
    }

    @Override
    public void transformEachIO(long start, long end, IntIOTransformer transformer) throws IOException {
        delegate.transformEachIO(start + shift, end+shift, (pos, old) -> transformer.transform(pos-shift, old));
    }

    @Override
    public int foldIO(int zero, long start, long end, IntBinaryIOOperation operator) throws IOException {
        return delegate.foldIO(zero, start + shift, end+shift, operator);
    }

    @Override
    public int fold(int zero, long start, long end, IntBinaryOperation operator){
        return delegate.fold(zero, start + shift, end+shift, operator);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {
        delegate.transferFrom(source, sourceStart, shift + arrayStart, shift + arrayEnd);
    }

    @Override
    public void force() {
        delegate.force();
    }

}
