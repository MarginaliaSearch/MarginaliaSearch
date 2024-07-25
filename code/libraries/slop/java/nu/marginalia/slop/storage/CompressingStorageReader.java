package nu.marginalia.slop.storage;

import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

public class CompressingStorageReader implements StorageReader {
    private final byte[] arrayBuffer;

    private long position = 0;

    private final InputStream is;
    private final ByteBuffer buffer;

    public CompressingStorageReader(Path path, StorageType storageType, ByteOrder order, int bufferSize) throws IOException {
        is = switch (storageType) {
            case GZIP -> new GZIPInputStream(Files.newInputStream(path, StandardOpenOption.READ));
            case ZSTD -> new ZstdCompressorInputStream(Files.newInputStream(path, StandardOpenOption.READ));
            default -> throw new UnsupportedEncodingException("Unsupported storage type: " + storageType);
        };

        this.arrayBuffer = new byte[bufferSize];
        this.buffer = ByteBuffer.wrap(arrayBuffer).order(order);

        buffer.position(0);
        buffer.limit(0);

        // read the first chunk, this is needed for InputStream otherwise we don't handle empty files
        // correctly
        refill();
    }

    @Override
    public byte getByte() throws IOException {
        if (buffer.remaining() < Byte.BYTES) {
            refill();
        }

        return buffer.get();
    }

    @Override
    public short getShort() throws IOException {
        if (buffer.remaining() < Short.BYTES) {
            refill();
        }

        return buffer.getShort();
    }

    @Override
    public char getChar() throws IOException {
        if (buffer.remaining() < Character.BYTES) {
            refill();
        }

        return buffer.getChar();
    }

    @Override
    public int getInt() throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            refill();
        }

        return buffer.getInt();
    }

    @Override
    public long getLong() throws IOException {
        if (buffer.remaining() < Long.BYTES) {
            refill();
        }

        return buffer.getLong();
    }

    @Override
    public float getFloat() throws IOException {
        if (buffer.remaining() < Float.BYTES) {
            refill();
        }

        return buffer.getFloat();
    }

    @Override
    public double getDouble() throws IOException {
        if (buffer.remaining() < Double.BYTES) {
            refill();
        }

        return buffer.getDouble();
    }

    @Override
    public void getBytes(byte[] bytes) throws IOException {
        getBytes(bytes, 0, bytes.length);
    }

    @Override
    public void getBytes(byte[] bytes, int offset, int length) throws IOException {
        if (buffer.remaining() >= length) {
            buffer.get(bytes, offset, length);
        } else {
            int totalToRead = length;

            while (totalToRead > 0) {
                if (!buffer.hasRemaining()) {
                    refill();
                }

                int toRead = Math.min(buffer.remaining(), totalToRead);
                buffer.get(bytes, offset + length - totalToRead, toRead);
                totalToRead -= toRead;
            }
        }
    }

    @Override
    public void getBytes(ByteBuffer data) throws IOException {
        if (data.remaining() < buffer.remaining()) {
            int lim = buffer.limit();
            buffer.limit(buffer.position() + data.remaining());
            data.put(buffer);
            buffer.limit(lim);
        } else {
            while (data.hasRemaining()) {
                if (!buffer.hasRemaining()) {
                    refill();
                }

                int lim = buffer.limit();
                buffer.limit(Math.min(buffer.position() + data.remaining(), lim));
                data.put(buffer);
                buffer.limit(lim);
            }
        }
    }

    public void getInts(int[] ints) throws IOException {
        if (buffer.remaining() >= ints.length * Integer.BYTES) {
            // fast path: if we can read all the ints from the buffer and don't need to check for buffer boundaries
            for (int i = 0; i < ints.length; i++) {
                ints[i] = buffer.getInt();
            }
        }
        else {
            for (int i = 0; i < ints.length; i++) {
                ints[i] = getInt();
            }
        }
    }

    public void getLongs(long[] longs) throws IOException {
        if (buffer.remaining() >= longs.length * Long.BYTES) {
            // fast path: if we can read all the longs from the buffer and don't need to check for buffer boundaries
            for (int i = 0; i < longs.length; i++) {
                longs[i] = buffer.getLong();
            }
        }
        else {
            for (int i = 0; i < longs.length; i++) {
                longs[i] = getLong();
            }
        }
    }

    @Override
    public void skip(long bytes, int stepSize) throws IOException {
        long toSkip = bytes * stepSize;

        if (buffer.remaining() < toSkip) {
            toSkip -= buffer.remaining();

            while (toSkip > 0) {
                long rb = is.skip(toSkip);
                toSkip -= rb;
                position += rb;
            }

            buffer.position(0);
            buffer.limit(0);
        } else {
            buffer.position(buffer.position() + (int) toSkip);
        }
    }

    @Override
    public void seek(long position, int stepSize) throws IOException {
        throw new UnsupportedEncodingException("Seek not supported in GzipStorageReader");
    }

    private void refill() throws IOException {
        buffer.compact();

        while (buffer.hasRemaining()) {
            int rb = is.read(arrayBuffer, buffer.position(), buffer.remaining());
            if (rb < 0) {
                break;
            }
            else {
                position += rb;
                buffer.position(buffer.position() + rb);
            }
        }

        buffer.flip();
    }

    @Override
    public long position() throws IOException {
        return position - buffer.remaining();
    }

    @Override
    public boolean hasRemaining() throws IOException {
        return buffer.hasRemaining() || is.available() > 0;
    }

    @Override
    public void close() throws IOException {
        is.close();
    }
}
