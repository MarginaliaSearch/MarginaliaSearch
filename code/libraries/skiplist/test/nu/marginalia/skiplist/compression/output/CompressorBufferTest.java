package nu.marginalia.skiplist.compression.output;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

class CompressorBufferTest {
    ByteBuffer bb;

    @BeforeEach
    public void setUp() {
        bb = ByteBuffer.allocate(24);
    }

    @Test
    public void testPut1() {
        var co = new CompressorBuffer(bb);
        co.put(254, 1);
        co.put(120, 1);
        co.put(99, 1);

        co.setPos(0);
        Assertions.assertEquals(254, co.get(1));
        Assertions.assertEquals(120, co.get(1));
        Assertions.assertEquals(99, co.get(1));
    }

    @Test
    public void testPut2() {
        var co = new CompressorBuffer(bb);
        co.put(1254, 2);
        co.put(120, 2);
        co.setPos(5);
        co.put(99, 2);

        co.setPos(0);

        Assertions.assertEquals(1254, co.get(2));
        Assertions.assertEquals(120, co.get(2));
        co.setPos(5);
        Assertions.assertEquals(99, co.get(2));
    }

    @Test
    public void testPut3() {
        var co = new CompressorBuffer(bb);
        co.setPos(0);
        co.put(0xFA_0533L, 3);
        co.setPos(4);
        co.put(0xFB_A1EEL, 3);

        co.setPos(0);
        Assertions.assertEquals(0xFA_0533L, co.get(3));
        co.setPos(4);
        Assertions.assertEquals(0xFB_A1EEL, co.get(3));
    }

    @Test
    public void testPut4() {
        var co = new CompressorBuffer(bb);
        co.setPos(0);
        co.put(0xFE1A_0533L, 4);
        co.setPos(5);
        co.put(0xF10B_A1EEL, 4);

        co.setPos(0);
        Assertions.assertEquals(0xFE1A_0533L, co.get(4));
        co.setPos(5);
        Assertions.assertEquals(0xF10B_A1EEL, co.get(4));
    }

    @Test
    public void testPut5() {
        var co = new CompressorBuffer(bb);
        co.setPos(0);
        co.put(0xF0FE1A_0533L, 5);
        co.setPos(8);
        co.put(0xFAF10B_A1EEL, 5);

        co.setPos(0);
        Assertions.assertEquals(0xF0FE1A_0533L, co.get(5));
        co.setPos(8);
        Assertions.assertEquals(0xFAF10B_A1EEL, co.get(5));
    }

    @Test
    public void testPut6() {
        var co = new CompressorBuffer(bb);
        co.setPos(0);
        co.put(0xF3F0FE1A_0533L, 6);
        co.setPos(8);
        co.put(0xF3FAF10B_A1EEL, 6);

        co.setPos(0);
        Assertions.assertEquals(0xF3F0FE1A_0533L, co.get(6));
        co.setPos(8);
        Assertions.assertEquals(0xF3FAF10B_A1EEL, co.get(6));
    }

    @Test
    public void testPut7() {
        var co = new CompressorBuffer(bb);
        co.setPos(0);
        co.put(0xFEF3F0FE1A_0533L, 7);
        co.setPos(8);
        co.put(0xF0F3FAF10B_A1EEL, 7);

        co.setPos(0);
        Assertions.assertEquals(0xFEF3F0FE1A_0533L, co.get(7));
        co.setPos(8);
        Assertions.assertEquals(0xF0F3FAF10B_A1EEL, co.get(7));
    }
}