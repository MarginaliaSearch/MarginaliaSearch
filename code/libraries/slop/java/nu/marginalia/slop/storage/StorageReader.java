package nu.marginalia.slop.storage;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface StorageReader extends AutoCloseable {
    byte getByte() throws IOException;
    short getShort() throws IOException;
    char getChar() throws IOException;
    int getInt() throws IOException;
    long getLong() throws IOException;
    float getFloat() throws IOException;
    double getDouble() throws IOException;

    void getBytes(byte[] bytes) throws IOException;
    void getBytes(byte[] bytes, int offset, int length) throws IOException;
    void getBytes(ByteBuffer buffer) throws IOException;

    void getInts(int[] ints) throws IOException;
    void getLongs(long[] longs) throws IOException;

    default void getChars(char[] chars) throws IOException {
        for (int i = 0; i < chars.length; i++) {
            chars[i] = getChar();
        }
    }
    default void getShorts(short[] shorts) throws IOException {
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = getShort();
        }
    }
    default void getFloats(float[] floats) throws IOException {
        for (int i = 0; i < floats.length; i++) {
            floats[i] = getFloat();
        }
    }
    default void getDoubles(double[] doubles) throws IOException {
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = getDouble();
        }
    }

    void skip(long bytes, int stepSize) throws IOException;
    void seek(long position, int stepSize) throws IOException;
    long position() throws IOException;
    boolean hasRemaining() throws IOException;

    @Override
    void close() throws IOException;
}
