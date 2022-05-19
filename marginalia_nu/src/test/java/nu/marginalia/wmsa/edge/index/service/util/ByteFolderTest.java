package nu.marginalia.wmsa.edge.index.service.util;

import nu.marginalia.util.ByteFolder;
import org.junit.jupiter.api.Test;

import static nu.marginalia.util.ByteFolder.decodeBytes;
import static org.junit.jupiter.api.Assertions.*;

class ByteFolderTest {

    @Test
    void foldBytes() {
        ByteFolder folder = new ByteFolder();
        // Edge cases
        assertArrayEquals(new byte[]{1,0}, folder.foldBytes(0,0));
        assertArrayEquals(new int[]{Integer.MAX_VALUE-1,Integer.MAX_VALUE}, decodeBytes(folder.foldBytes(Integer.MAX_VALUE-1,Integer.MAX_VALUE)));
        assertArrayEquals(new int[]{128, 1}, decodeBytes(folder.foldBytes(128,1)));

        // 1 byte boundary
        for (int i = 0; i < 512; i++) {
            for (int j = 0; j < 512; j++) {
                assertArrayEquals(new int[]{i,j}, decodeBytes(folder.foldBytes(i,j)), "Discrepancy @ " + i + " ," + j );
            }
        }

        // Scattershot
        for (int i = 0; i < 1_000_000; i++) {
            int p = (int) (Integer.MAX_VALUE * Math.random());
            int q = (int) (Integer.MAX_VALUE * Math.random());
            assertArrayEquals(new int[]{q,p}, decodeBytes(folder.foldBytes(q,p)), "Discrepancy @ " + q + " ," + p );
        }

    }

}