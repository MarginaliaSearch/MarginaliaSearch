package nu.marginalia.slop.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SimpleStorageWriterAndReaderTest {
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void cleanup() {
        try {
            Files.walk(tempDir)
                    .sorted(this::deleteOrder)
                    .forEach(p -> {
                        try {
                            if (Files.isRegularFile(p)) {
                                System.out.println("Deleting " + p + " " + Files.size(p));
                            }
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    int deleteOrder(Path a, Path b) {
        if (Files.isDirectory(a) && !Files.isDirectory(b)) {
            return 1;
        } else if (!Files.isDirectory(a) && Files.isDirectory(b)) {
            return -1;
        } else {
            return a.getNameCount() - b.getNameCount();
        }
    }

    Path tempFile() {
        try {
            return Files.createTempFile(tempDir, getClass().getSimpleName(), ".dat");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    StorageWriter writer(Path path) {
        try {
            return new SimpleStorageWriter(path, ByteOrder.LITTLE_ENDIAN, 63);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    StorageReader reader(Path path) {
        try {
            return new SimpleStorageReader(path, ByteOrder.LITTLE_ENDIAN, 63);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void putByte() throws IOException {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, writer.position());
                writer.putByte((byte) i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertTrue(reader.hasRemaining());
                assertEquals(i, reader.position());

                assertEquals((byte) i, reader.getByte());
            }
            assertFalse(reader.hasRemaining());
        }
    }

    @Test
    void putByteSkipReader() throws IOException {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, writer.position());
                writer.putByte((byte) i);
            }
        }

        try (var reader = reader(p)) {
            assertEquals(0, reader.position());
            assertEquals((byte) 0, reader.getByte());
            assertEquals(1, reader.position());
            assertEquals((byte) 1, reader.getByte());
            reader.skip(64, 1);
            assertEquals(66, reader.position());
            assertEquals((byte) 66, reader.getByte());
            assertEquals(67, reader.position());
            reader.skip(2, 3);
            assertEquals(73, reader.position());
            assertEquals((byte) 73, reader.getByte());
        }
    }

    @Test
    void putShort() throws IOException {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putByte((byte) i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals((byte) i, reader.getByte());
            }
        }
    }

    @Test
    void putChar() throws IOException {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putChar((char) i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals((char) i, reader.getChar());
            }
        }
    }

    @Test
    void putInt() throws IOException  {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putInt(i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, reader.getInt());
            }
        }
    }

    @Test
    void putLong() throws IOException  {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putLong(i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, reader.getLong());
            }
        }
    }

    @Test
    void putFloat() throws IOException {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putFloat(i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, reader.getFloat());
            }
        }
    }

    @Test
    void putDouble() throws IOException  {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                writer.putDouble(i);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                assertEquals(i, reader.getDouble());
            }
        }
    }

    @Test
    void putBytes() throws IOException  {
        Path p = tempFile();


        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                byte[] data = new byte[2];
                data[0] = (byte) i;
                data[1] = (byte) (i + 1);
                writer.putBytes(data);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                byte[] data = new byte[2];
                reader.getBytes(data);
                assertEquals((byte) i, data[0]);
                assertEquals((byte) (i + 1), data[1]);
            }
        }
    }

    @Test
    void testPutBytes() throws IOException  {
        Path p = tempFile();

        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                byte[] data = new byte[4];
                data[1] = (byte) i;
                data[2] = (byte) (i + 1);
                writer.putBytes(data, 1, 2);
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                byte[] data = new byte[4];
                reader.getBytes(data, 1, 2);
                assertEquals((byte) i, data[1]);
                assertEquals((byte) (i + 1), data[2]);
            }
        }
    }

    @Test
    void testPutBytesViaBuffer() throws IOException {
        Path p = tempFile();

        ByteBuffer buffer = ByteBuffer.allocate(4);
        try (var writer = writer(p)) {
            for (int i = 0; i < 127; i++) {
                buffer.clear();
                buffer.put(new byte[] { (byte) i, (byte) (i+1), (byte) (i + 2), (byte) (i+3) });
                buffer.flip();
                writer.putBytes(buffer);

                assertFalse(buffer.hasRemaining());
            }
        }

        try (var reader = reader(p)) {
            for (int i = 0; i < 127; i++) {
                buffer.clear();
                reader.getBytes(buffer);
                buffer.flip();

                assertEquals(4, buffer.remaining());

                assertEquals((byte) i, buffer.get());
                assertEquals((byte) (i + 1), buffer.get());
                assertEquals((byte) (i + 2), buffer.get());
                assertEquals((byte) (i + 3), buffer.get());

                assertFalse(buffer.hasRemaining());
            }
        }
    }
}