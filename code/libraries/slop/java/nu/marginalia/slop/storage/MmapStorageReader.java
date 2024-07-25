package nu.marginalia.slop.storage;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@SuppressWarnings("preview") // for MemorySegment
public class MmapStorageReader implements StorageReader {
    private final MemorySegment segment;
    private final Arena arena;

    private long position = 0;

    public MmapStorageReader(Path path) throws IOException {
        arena = Arena.ofConfined();

        try (var channel = (FileChannel) Files.newByteChannel(path, StandardOpenOption.READ)) {
            this.segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
        }

        position = 0;
    }

    @Override
    public byte getByte() throws IOException {
        return segment.get(ValueLayout.JAVA_BYTE, position++);
    }

    @Override
    public short getShort() throws IOException {
        short ret = segment.get(ValueLayout.JAVA_SHORT, position);
        position += Short.BYTES;
        return ret;

    }

    @Override
    public char getChar() throws IOException {
        char ret = segment.get(ValueLayout.JAVA_CHAR, position);
        position += Character.BYTES;
        return ret;
    }

    @Override
    public int getInt() throws IOException {
        int ret = segment.get(ValueLayout.JAVA_INT, position);
        position += Integer.BYTES;
        return ret;
    }

    @Override
    public long getLong() throws IOException {
        long ret = segment.get(ValueLayout.JAVA_LONG, position);
        position += Long.BYTES;
        return ret;
    }

    @Override
    public float getFloat() throws IOException {
        float ret = segment.get(ValueLayout.JAVA_FLOAT, position);
        position += Float.BYTES;
        return ret;
    }

    @Override
    public double getDouble() throws IOException {
        double ret = segment.get(ValueLayout.JAVA_DOUBLE, position);
        position += Double.BYTES;
        return ret;
    }

    @Override
    public void getBytes(byte[] bytes) throws IOException {
        if (position + bytes.length > segment.byteSize()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = segment.get(ValueLayout.JAVA_BYTE, position+i);
        }
        position += bytes.length;
    }

    @Override
    public void getBytes(byte[] bytes, int offset, int length) throws IOException {
        if (position + length > segment.byteSize()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int i = 0; i < length; i++) {
            bytes[offset + i] = segment.get(ValueLayout.JAVA_BYTE, position+i);
        }
        position += length;
    }

    @Override
    public void getBytes(ByteBuffer buffer) throws IOException {
        int toRead = buffer.remaining();
        if (position + toRead > segment.byteSize()) {
            throw new ArrayIndexOutOfBoundsException();
        }

        buffer.put(segment.asSlice(position, toRead).asByteBuffer());
        position += toRead;
    }

    public void getInts(int[] ret) {
        for (int i = 0; i < ret.length; i++) {
            ret[i] = segment.get(ValueLayout.JAVA_INT, position);
            position += Integer.BYTES;
        }
    }

    public void getLongs(long[] ret) {
        for (int i = 0; i < ret.length; i++) {
            ret[i] = segment.get(ValueLayout.JAVA_LONG, position);
            position += Long.BYTES;
        }
    }

    @Override
    public void skip(long bytes, int stepSize) throws IOException {
        position += bytes * stepSize;
    }

    @Override
    public void seek(long position, int stepSize) throws IOException {
        this.position = position * stepSize;
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public boolean hasRemaining() throws IOException {
        return position < segment.byteSize();
    }

    @Override
    public void close() throws IOException {
        arena.close();
    }
}
