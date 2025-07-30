package nu.marginalia.array.algo;

import nu.marginalia.NativeAlgos;
import nu.marginalia.array.DirectFileReader;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public class NativeAlgosTest {
    @Test
    public void test() throws IOException {
        LongArray array = LongArrayFactory.mmapForWritingShared(Path.of("/tmp/test"), 1024);
        for (int i = 0; i < 1024; i++) {
            array.set(i, i);
        }
        array.close();

        var ms = Arena.global().allocate(512, 8);

        int fd = NativeAlgos.openDirect(Path.of("/tmp/test"));
        int ret = NativeAlgos.readAt(fd, ms, 512);
        System.out.println(ret);
        System.out.println(ms.byteSize());
        NativeAlgos.closeFd(fd);

        var array2 = LongArrayFactory.wrap(ms);
        for (int i = 0; i < array2.size(); i++) {
            System.out.println(i + ": " + array2.get(i));
        }

    }

    @Test
    void testDirectFileReader() throws IOException {
        LongArray array = LongArrayFactory.mmapForWritingShared(Path.of("/tmp/test"), 1024);
        for (int i = 0; i < 1024; i++) {
            array.set(i, i);
        }
        array.close();

        try (var dfr = new DirectFileReader(Path.of("/tmp/test"))) {
            LongArray array2 = LongArrayFactory.onHeapConfined(64);
            dfr.read(array2, 0);
            for (int i = 0; i < array2.size(); i++) {
                System.out.println(i + ": " + array2.get(i));
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
