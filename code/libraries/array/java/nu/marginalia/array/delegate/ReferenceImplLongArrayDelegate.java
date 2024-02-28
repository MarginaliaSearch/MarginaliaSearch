package nu.marginalia.array.delegate;

import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.LongArray;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ReferenceImplLongArrayDelegate implements LongArray {

    private final LongArray delegate;

    public ReferenceImplLongArrayDelegate(LongArray delegate) {
        this.delegate = delegate;
    }

    @Override
    public long get(long pos) {
        return delegate.get(pos);
    }

    @Override
    public void set(long pos, long value) {
        delegate.set(pos, value);
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public void write(Path file) throws IOException {
        delegate.write(file);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {
        delegate.transferFrom(source, sourceStart, arrayStart, arrayEnd);
    }

    @Override
    public ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end) {
        return delegate.directRangeIfPossible(start, end);
    }

    @Override
    public void force() {
        delegate.force();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
