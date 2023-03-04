package nu.marginalia.array.page;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.IntArray;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IntArrayPage implements PartitionPage, IntArray {

    final IntBuffer intBuffer;
    final ByteBuffer byteBuffer;

    private IntArrayPage(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.intBuffer = byteBuffer.asIntBuffer();
    }

    public static IntArrayPage onHeap(int size) {
        return new IntArrayPage(ByteBuffer.allocateDirect(WORD_SIZE*size));
    }

    public static IntArrayPage fromMmapReadOnly(Path file, long offset, int size) throws IOException {
        return new IntArrayPage(mmapFile(file, offset, size, FileChannel.MapMode.READ_ONLY, StandardOpenOption.READ));
    }

    public static IntArrayPage fromMmapReadWrite(Path file, long offset, int size) throws IOException {
        return new IntArrayPage(mmapFile(file, offset, size, FileChannel.MapMode.READ_WRITE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
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
    public int get(long at) {
        return intBuffer.get((int) at);
    }

    @Override
    public void get(long start, long end, int[] buffer) {
        intBuffer.get((int) start, buffer, 0, (int) (end - start));
    }

    @Override
    public void set(long at, int val) {
        intBuffer.put((int) at, val);
    }

    @Override
    public void set(long start, long end, IntBuffer buffer, int bufferStart) {
        intBuffer.put((int) start, buffer, bufferStart, (int) (end-start));
    }

    @Override
    public long size() {
        return intBuffer.capacity();
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
