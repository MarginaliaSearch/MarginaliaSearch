package nu.marginalia.rwf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomWriteFunnelTest {

    Path testFile;
    @BeforeEach
    public void setUp() throws IOException {
        testFile = Files.createTempFile(getClass().getSimpleName(), "bin");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(testFile);
    }
    @Test
    public void test() {
        try (var funnel = new RandomWriteFunnel(Path.of("/tmp"), 5001);
             var out = Files.newByteChannel(testFile, StandardOpenOption.WRITE)) {
            for (int i = 10_000-1; i >= 0; i--) {
                System.out.println(i);
                funnel.put(i, 10_000-i);
            }
            funnel.write(out);

        } catch (Exception e) {
            e.printStackTrace();
        }

        try (var in = new RandomAccessFile(testFile.toFile(), "r")) {
            for (int i = 0; i < 10_000; i++) {
                assertEquals(10_000-i, Long.reverseBytes(in.readLong()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSparse() {
        for (int j = 1; j <= 20; j++) {
            try (var funnel = new RandomWriteFunnel(Path.of("/tmp"), j);
                 var out = Files.newByteChannel(testFile, StandardOpenOption.WRITE)) {
                for (int i = 10 - 1; i >= 0; i -= 2) {
                    funnel.put(i, 10 - i);
                }
                funnel.write(out);

            } catch (Exception e) {
                e.printStackTrace();
            }

            try (var in = new RandomAccessFile(testFile.toFile(), "r")) {
                assertEquals(0, Long.reverseBytes(in.readLong()));
                assertEquals(9, Long.reverseBytes(in.readLong()));
                assertEquals(0, Long.reverseBytes(in.readLong()));
                assertEquals(7, Long.reverseBytes(in.readLong()));
                assertEquals(0, Long.reverseBytes(in.readLong()));
                assertEquals(5, Long.reverseBytes(in.readLong()));
                assertEquals(0, Long.reverseBytes(in.readLong()));
                assertEquals(3, Long.reverseBytes(in.readLong()));
                assertEquals(0, Long.reverseBytes(in.readLong()));
                assertEquals(1, Long.reverseBytes(in.readLong()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    public void testYuge() {
        for (int j = 1; j <= 20; j++) {
            try (var funnel = new RandomWriteFunnel(Path.of("/tmp"), j);
                 var out = Files.newByteChannel(testFile, StandardOpenOption.WRITE)) {
                for (int i = 10 - 1; i >= 0; i -= 2) {
                    funnel.put(i, Long.MAX_VALUE - i);
                }
                funnel.write(out);

            } catch (Exception e) {
                e.printStackTrace();
            }

            try (var in = new RandomAccessFile(testFile.toFile(), "r")) {
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
                in.readLong();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}