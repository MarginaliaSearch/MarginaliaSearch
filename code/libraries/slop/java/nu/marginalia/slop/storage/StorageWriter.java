package nu.marginalia.slop.storage;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Interface for writing data to a storage. */
public interface StorageWriter extends AutoCloseable {
    void putByte(byte b) throws IOException;
    void putShort(short s) throws IOException;
    void putChar(char c) throws IOException;
    void putInt(int i) throws IOException;
    void putLong(long l) throws IOException;

    void putFloat(float f) throws IOException;
    void putDouble(double d) throws IOException;

    void putBytes(byte[] bytes) throws IOException;
    void putBytes(byte[] bytes, int offset, int length) throws IOException;
    void putBytes(ByteBuffer buffer) throws IOException;

    // Bulk operations, these can be more efficient than the single value operations
    // if they are implemented in a way that minimizes the of bounds checks and other overhead

    void putInts(int[] bytes) throws IOException;
    void putLongs(long[] bytes) throws IOException;

    default void putChars(char[] chars) throws IOException {
        for (char c : chars) {
            putChar(c);
        }
    }
    default void putShorts(short[] shorts) throws IOException {
        for (short s : shorts) {
            putShort(s);
        }
    }
    default void putFloats(float[] floats) throws IOException {
        for (float f : floats) {
            putFloat(f);
        }
    }
    default void putDoubles(double[] doubles) throws IOException {
        for (double d : doubles) {
            putDouble(d);
        }
    }

    long position() throws IOException;
    void close() throws IOException;
}
