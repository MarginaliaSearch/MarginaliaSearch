package nu.marginalia.slop.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SimpleStorageReader implements StorageReader {
    private final ByteBuffer buffer;
    private final FileChannel channel;

    public SimpleStorageReader(Path path, ByteOrder order, int bufferSize) throws IOException {
        channel = (FileChannel) Files.newByteChannel(path, StandardOpenOption.READ);

        this.buffer = ByteBuffer.allocateDirect(bufferSize).order(order);

        buffer.position(0);
        buffer.limit(0);
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
            channel.position(channel.position() - buffer.remaining() + toSkip);
            buffer.position(0);
            buffer.limit(0);
        } else {
            buffer.position(buffer.position() + (int) toSkip);
        }
    }

    @Override
    public void seek(long position, int stepSize) throws IOException {
        position *= stepSize;

        if (position > channel.position() - buffer.limit() && position < channel.position()) {
            // If the position is within the buffer, we can just move the buffer position to the correct spot
            buffer.position((int) (position - channel.position() + buffer.limit()));
        }
        else {
            // Otherwise, we need to move the channel position and invalidate the buffer
            channel.position(position);
            buffer.position(0);
            buffer.limit(0);
        }
    }

    private void refill() throws IOException {
        buffer.compact();

        while (buffer.hasRemaining()) {
            if (channel.read(buffer) == -1) {
                break;
            }
        }

        buffer.flip();
    }

    @Override
    public long position() throws IOException {
        return channel.position() - buffer.remaining();
    }

    @Override
    public boolean hasRemaining() throws IOException {
        return buffer.hasRemaining() || channel.position() < channel.size();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
