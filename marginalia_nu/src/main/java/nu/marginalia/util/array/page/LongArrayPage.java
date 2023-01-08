package nu.marginalia.util.array.page;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.trace.ArrayTrace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LongArrayPage implements PartitionPage, LongArray {

    final ArrayTrace trace = ArrayTrace.get(this);

    final LongBuffer longBuffer;
    final ByteBuffer byteBuffer;

    private LongArrayPage(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.longBuffer = byteBuffer.asLongBuffer();
    }

    public static LongArrayPage onHeap(int size) {
        return new LongArrayPage(ByteBuffer.allocateDirect(WORD_SIZE*size));
    }

    public static LongArrayPage fromMmapReadOnly(Path file, long offset, int size) throws IOException {
        return new LongArrayPage(mmapFile(file, offset, size, FileChannel.MapMode.READ_ONLY, StandardOpenOption.READ));
    }

    public static LongArrayPage fromMmapReadWrite(Path file, long offset, int size) throws IOException {
        return new LongArrayPage(mmapFile(file, offset, size, FileChannel.MapMode.READ_WRITE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    }

    private static ByteBuffer mmapFile(Path file, long offset, int size, FileChannel.MapMode mode, OpenOption... openOptions) throws IOException {
        try (var channel = (FileChannel) Files.newByteChannel(file, openOptions)) {
            return channel.map(mode, WORD_SIZE*offset, (long) size*WORD_SIZE);
        }
        catch (IOException ex) {
            throw new IOException("Failed to map file " + file + " (" + offset + ":" + size + ")", ex);
        }
    }

    @Override
    public long get(long at) {
        try {
            trace.touch(at);

            return longBuffer.get((int) at);
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("@" + at + "(" + 0 + ":" + longBuffer.capacity() + ")");
        }
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        trace.touch(start, end);

        longBuffer.get((int) start, buffer, 0, (int) (end - start));
    }

    @Override
    public void set(long at, long val) {
        trace.touch(at);

        longBuffer.put((int) at, val);
    }

    @Override
    public void set(long start, long end, LongBuffer buffer, int bufferStart) {
        longBuffer.put((int) start, buffer, bufferStart, (int) (end-start));
    }

    @Override
    public long size() {
        return longBuffer.capacity();
    }

    public void increment(int at) {
        set(at, get(at) + 1);
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    @Override
    public void write(Path filename) throws IOException {
        try (var channel = (FileChannel) Files.newByteChannel(filename, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            write(channel);
        }
    }

    @Override
    public void force() {
        if (byteBuffer instanceof MappedByteBuffer mb) {
            mb.force();
        }
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {

        trace.touch(arrayStart, arrayEnd);

        int index = (int) (arrayStart * WORD_SIZE);
        int length = (int) ((arrayEnd - arrayStart) * WORD_SIZE);

        var slice = byteBuffer.slice(index, length);

        long startPos = sourceStart * WORD_SIZE;
        while (slice.position() < slice.capacity()) {
            source.read(slice, startPos + slice.position());
        }
    }

    @Override
    public void advice(NativeIO.Advice advice) throws IOException {
        NativeIO.madvise((MappedByteBuffer) byteBuffer, advice);
    }

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {
        NativeIO.madviseRange((MappedByteBuffer) byteBuffer, advice, (int) start, (int) (end-start));
    }

}
