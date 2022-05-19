package nu.marginalia.wmsa.edge.index.service.util;

import nu.marginalia.util.RandomWriteFunnel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomWriteFunnelTest {

    @Test
    public void test() {
        new File("/tmp/test.bin").delete();
        try (var funnel = new RandomWriteFunnel(Path.of("/tmp"), 10_000, 5001);
             var out = new RandomAccessFile("/tmp/test.bin", "rw")) {
            for (int i = 10_000-1; i >= 0; i--) {
                System.out.println(i);
                funnel.put(i, 10_000-i);
            }
            funnel.write(out.getChannel());

        } catch (Exception e) {
            e.printStackTrace();
        }

        try (var in = new RandomAccessFile("/tmp/test.bin", "r")) {
            for (int i = 0; i < 10_000; i++) {
                assertEquals(10_000-i, in.readLong());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSparse() {
        new File("/tmp/test.bin").delete();
        for (int j = 1; j <= 20; j++) {
            try (var funnel = new RandomWriteFunnel(Path.of("/tmp"), 10, j);
                 var out = new RandomAccessFile("/tmp/test.bin", "rw")) {
                for (int i = 10 - 1; i >= 0; i -= 2) {
                    funnel.put(i, 10 - i);
                }
                funnel.write(out.getChannel());

            } catch (Exception e) {
                e.printStackTrace();
            }

            try (var in = new RandomAccessFile("/tmp/test.bin", "r")) {
                assertEquals(0, in.readLong());
                assertEquals(9, in.readLong());
                assertEquals(0, in.readLong());
                assertEquals(7, in.readLong());
                assertEquals(0, in.readLong());
                assertEquals(5, in.readLong());
                assertEquals(0, in.readLong());
                assertEquals(3, in.readLong());
                assertEquals(0, in.readLong());
                assertEquals(1, in.readLong());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}