package nu.marginalia.wmsa.edge.index.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicroBTreeCachedIndexTest {
    MicroCache mc;

    @BeforeEach
    public void setUp() {
        mc = new MicroCache(8);
    }

    @Test
    public void testSunnyDay() {
        mc.set(1, 2);
        mc.set(2, 4);
        mc.set(4, 8);
        mc.set(8, 16);
        assertEquals(2, mc.get(1));
        assertEquals(4, mc.get(2));
        assertEquals(8, mc.get(4));
        assertEquals(16, mc.get(8));
        assertEquals(MicroCache.BAD_VALUE, mc.get(16));
    }

    @Test
    public void testRollOver() {
        mc.set(1, 2);
        mc.set(2, 4);
        mc.set(4, 8);
        mc.set(8, 16);
        mc.set(16, 32);
        mc.set(32, 64);
        mc.set(64, 128);
        mc.set(128, 256);
        mc.set(256, 512);

        assertEquals(MicroCache.BAD_VALUE, mc.get(1));
        assertEquals(4, mc.get(2));
        assertEquals(8, mc.get(4));
        assertEquals(16, mc.get(8));
        assertEquals(32, mc.get(16));
        assertEquals(64, mc.get(32));
        assertEquals(128, mc.get(64));
        assertEquals(256, mc.get(128));
        assertEquals(512, mc.get(256));
    }

}