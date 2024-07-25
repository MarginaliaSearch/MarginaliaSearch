package nu.marginalia.slop.storage;

import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

public class CompressingStorageWriter implements StorageWriter, AutoCloseable {
    private final ByteBuffer buffer;
    private final OutputStream os;
    private byte[] arrayBuffer;

    private long position = 0;

    private final Path tempPath;
    private final Path destPath;

    public CompressingStorageWriter(Path path, StorageType storageType, ByteOrder order, int bufferSize) throws IOException {
        tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        destPath = path;

        os = switch (storageType) {
            case GZIP -> new GZIPOutputStream(Files.newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
            case ZSTD -> new ZstdCompressorOutputStream(Files.newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
            default -> throw new IllegalArgumentException("Unsupported storage type: " + storageType);
        };

        arrayBuffer = new byte[bufferSize];
        this.buffer = ByteBuffer.wrap(arrayBuffer).order(order);
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

        int rem = buffer.remaining();
        if (rem > 0) {
            os.write(buffer.array(), buffer.position(), buffer.remaining());
            buffer.limit(0);
            position += rem;
        }

        buffer.clear();
    }

    public long position() throws IOException {
        return position + buffer.position();
    }

    @Override
    public void close() throws IOException {
        flush();

        os.flush();
        os.close();

        Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
