package nu.marginalia.array;

import com.upserve.uppend.blobs.NativeIO;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/** This is a benchmark comparison implementation of LongArray that
 *  does not include the optimizations in PagingLongArray */
public class SimulatedNaiveArray implements LongArray {
    final LongBuffer[] buffers;
    final int bufferSize;

    public SimulatedNaiveArray(int size, int bufferSize) {
        this.bufferSize = bufferSize;
        buffers = new LongBuffer[size / bufferSize];
        for (int i = 0 ; i < buffers.length; i++) {
            buffers[i] = LongBuffer.allocate(bufferSize);
        }
    }

    @Override
    public ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end) {
        return null;
    }

    @Override
    public void force() {}

    @Override
    public void close() {
    }

    @Override
    public void advice(NativeIO.Advice advice) throws IOException {}

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {}

    @Override
    public long get(long pos) {
        return buffers[(int) pos/bufferSize].get((int) pos%bufferSize);
    }

    @Override
    public void set(long pos, long value) {
        buffers[(int) pos/bufferSize].put((int) pos%bufferSize, value);
    }

    @Override
    public long size() {
        return 1024;
    }

    @Override
    public void write(Path file) throws IOException {}
    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {}
}