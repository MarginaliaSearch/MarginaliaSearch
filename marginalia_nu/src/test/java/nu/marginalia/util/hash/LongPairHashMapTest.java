package nu.marginalia.util.hash;

import nu.marginalia.util.multimap.MultimapFileLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class LongPairHashMapTest {

    @Test
    public void test() throws IOException {

        var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
        Set<Integer> toPut = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            while (!toPut.add((int)(Integer.MAX_VALUE * Math.random())));
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw");
            MultimapFileLong mmf = new MultimapFileLong(raf, FileChannel.MapMode.READ_WRITE, 10000, 1000);
            var lphm = LongPairHashMap.createNew(mmf, 1024);
            toPut.forEach(i -> {
                lphm.put(new LongPairHashMap.CellData(i, i));
            });
            mmf.force();
            lphm.close();

            RandomAccessFile raf2 = new RandomAccessFile(tempFile.toFile(), "rw");
            MultimapFileLong mmf2 = new MultimapFileLong(raf2, FileChannel.MapMode.READ_WRITE, 10000, 1000);
            var lphm2 = LongPairHashMap.loadExisting(mmf2);
            toPut.forEach(i -> {
                Assertions.assertTrue(lphm2.get(i).isSet());
                Assertions.assertEquals(i, (int) lphm2.get(i).getKey());
                Assertions.assertEquals(i, (int) lphm2.get(i).getOffset());
            });

            for (int i = 0; i < 10_000_000; i++) {
                if (!toPut.contains(i)) {
                    Assertions.assertFalse(lphm2.get(i).isSet());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Files.delete(tempFile);
        }
    }

}