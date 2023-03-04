package nu.marginalia.array.delegate;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.IntArray;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ReferenceImplIntArrayDelegate implements IntArray {

    private final IntArray delegate;

    public ReferenceImplIntArrayDelegate(IntArray delegate) {
        this.delegate = delegate;
    }

    @Override
    public int get(long pos) {
        return delegate.get(pos);
    }

    @Override
    public void set(long pos, int value) {
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
    public void force() {
        delegate.force();
    }


    @Override
    public void advice(NativeIO.Advice advice) throws IOException {
        delegate.advice(advice);
    }

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {
        delegate.advice(advice, start, end);
    }
}
