package nu.marginalia.slop.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class SimpleStorageWriter implements StorageWriter, AutoCloseable {
    private final ByteBuffer buffer;
    private final FileChannel channel;

    private final Path tempPath;
    private final Path destPath;

    public SimpleStorageWriter(Path path, ByteOrder order, int bufferSize) throws IOException {
        tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        destPath = path;

        channel = (FileChannel) Files.newByteChannel(tempPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        this.buffer = ByteBuffer.allocate(bufferSize).order(order);
    }

    @Override
    public void putByte(byte b) throws IOException {
        if (buffer.remaining() < Byte.BYTES) {
            flush();
        }

        buffer.put(b);
    }

    @Override
    public void putShort(short s) throws IOException {
        if (buffer.remaining() < Short.BYTES) {
            flush();
        }

        buffer.putShort(s);
    }

    @Override
    public void putChar(char s) throws IOException {
        if (buffer.remaining() < Character.BYTES) {
            flush();
        }

        buffer.putChar(s);
    }

    @Override
    public void putInt(int i) throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            flush();
        }

        buffer.putInt(i);
    }

    @Override
    public void putLong(long l) throws IOException {
        if (buffer.remaining() < Long.BYTES) {
            flush();
        }

        buffer.putLong(l);
    }

    @Override
    public void putInts(int[] values) throws IOException {
        if (buffer.remaining() >= Integer.BYTES * values.length) {
            for (int value : values) {
                buffer.putInt(value);
            }
        }
        else {
            for (int value : values) {
                putInt(value);
            }
        }
    }

    @Override
    public void putLongs(long[] values) throws IOException {
        if (buffer.remaining() >= Long.BYTES * values.length) {
            for (long value : values) {
                buffer.putLong(value);
            }
        }
        else {
            for (long value : values) {
                putLong(value);
            }
        }
    }

    @Override
    public void putBytes(byte[] bytes) throws IOException {
        putBytes(bytes, 0, bytes.length);
    }

    @Override
    public void putBytes(byte[] bytes, int offset, int length) throws IOException {
        int totalToWrite = length;

        if (totalToWrite < buffer.remaining()) {
            buffer.put(bytes, offset, totalToWrite);
        }
        else { // case where the data is larger than the write buffer, so we need to write in chunks
            while (totalToWrite > 0) {
                if (!buffer.hasRemaining()) {
                    flush();
                }

                // Write as much as possible to the buffer
                int toWriteNow = Math.min(totalToWrite, buffer.remaining());
                buffer.put(bytes, offset, toWriteNow);

                // Update the remaining bytes and offset
                totalToWrite -= toWriteNow;
                offset += toWriteNow;
            }
        }
    }

    @Override
    public void putBytes(ByteBuffer data) throws IOException {
        if (data.remaining() < buffer.remaining()) {
            buffer.put(data);
        }
        else { // case where the data is larger than the write buffer, so we need to write in chunks
            while (data.hasRemaining()) {
                if (!buffer.hasRemaining()) {
                    flush();
                }

                // temporarily reduce the data buffer's limit to what's possible to write to the writer's buffer
                int lim = data.limit();
                data.limit(Math.min(data.position() + buffer.remaining(), lim));

                // write the data to the buffer
                buffer.put(data);

                // restore the limit, so we can write the rest of the data
                data.limit(lim);
            }
        }
    }

    @Override
    public void putFloat(float f) throws IOException {
        if (buffer.remaining() < Float.BYTES) {
            flush();
        }

        buffer.putFloat(f);
    }

    @Override
    public void putDouble(double d) throws IOException {
        if (buffer.remaining() < Double.BYTES) {
            flush();
        }

        buffer.putDouble(d);
    }

    private void flush() throws IOException {
        buffer.flip();

        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }

        buffer.clear();
    }

    public long position() throws IOException {
        return channel.position() + buffer.position();
    }

    @Override
    public void close() throws IOException {
        flush();

        channel.force(false);
        channel.close();

        Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
