package nu.marginalia.sequence;

import nu.marginalia.sequence.io.BitReader;
import nu.marginalia.sequence.io.BitWriter;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BitWriterTest {

    @Test
    public void testPutBitsFullByte() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.putBit(false);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(false);

        var out = writer.finish();

        byte actual = out.get(0);
        byte expected = (byte) 0b0111_1110;

        assertEquals(expected, actual);
        assertEquals(1, out.capacity());
    }

    @Test
    public void testPutBitsPartialByte() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.putBit(true);
        writer.putBit(false);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);

        var out = writer.finish();

        byte actual = out.get(0);
        byte expected = (byte) 0b1011_1110;

        assertEquals(expected, actual, STR."was \{Integer.toBinaryString(actual & 0xFF)}");
        assertEquals(1, out.capacity());
    }


    @Test
    public void testPutBitsOneAndAHalfByte() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.putBit(true);
        writer.putBit(false);
        writer.putBit(true);
        writer.putBit(true);

        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(true);
        writer.putBit(false);

        writer.putBit(true);
        writer.putBit(true);

        var out = writer.finish();

        assertEquals(2, out.capacity());

        byte actual1 = out.get(0);
        byte actual2 = out.get(1);
        byte expected1 = (byte) 0b1011_1110;
        byte expected2 = (byte) 0b1100_0000;

        assertEquals(expected1, actual1, STR."was \{Integer.toBinaryString(actual1 & 0xFF)}");
        assertEquals(expected2, actual2, STR."was \{Integer.toBinaryString(actual2 & 0xFF)}");

    }


    @Test
    public void testPutBitsIntOverflow() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        for (int i = 0; i < 4; i++) {
            writer.putBit(true);
            writer.putBit(false);
            writer.putBit(true);
            writer.putBit(true);

            writer.putBit(true);
            writer.putBit(true);
            writer.putBit(true);
            writer.putBit(false);
        }
        writer.putBit(true);
        writer.putBit(true);


        var out = writer.finish();

        assertEquals(5, out.capacity());

        for (int i = 0; i < 4; i++) {
            byte actual1 = out.get(i);
            byte expected1 = (byte) 0b1011_1110;

            assertEquals(expected1, actual1, STR."was \{Integer.toBinaryString(actual1 & 0xFF)}");
        }

        byte actual2 = out.get(4);
        byte expected2 = (byte) 0b1100_0000;

        assertEquals(expected2, actual2, STR."was \{Integer.toBinaryString(actual2 & 0xFF)}");

    }

    @Test
    public void testPut1() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(1, 1);
        var ret = writer.finish();
        assertEquals(1, ret.capacity());
        assertEquals((byte)0b1000_0000, ret.get(0));
    }

    @Test
    public void testPut4() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(1, 4);
        var ret = writer.finish();
        assertEquals(1, ret.capacity());
        assertEquals((byte)0b0001_0000, ret.get(0));
    }

    @Test
    public void testPut8() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(3, 8);
        var ret = writer.finish();
        assertEquals(1, ret.capacity());
        assertEquals((byte)0b0000_0011, ret.get(0));
    }

    @Test
    public void testPut8_2() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(~0, 8);
        var ret = writer.finish();
        assertEquals(1, ret.capacity());
        assertEquals((byte)0b1111_1111, ret.get(0));
    }

    @Test
    public void testPut8_3() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(~0, 8);
        writer.put(0, 8);
        writer.put(~0, 8);
        writer.put(1, 1);

        var ret = writer.finish();

        assertEquals(4, ret.capacity());
        assertEquals((byte)0b1111_1111, ret.get(0));
        assertEquals((byte)0, ret.get(1));
        assertEquals((byte)0b1111_1111, ret.get(2));
        assertEquals((byte)0b1000_0000, ret.get(3));
    }

    @Test
    public void testIntOverflow() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(~0, 24);
        writer.put(0, 16);
        writer.put(1, 1);

        var ret = writer.finish();

        assertEquals(6, ret.capacity());
        assertEquals((byte)0b1111_1111, ret.get(0));
        assertEquals((byte)0b1111_1111, ret.get(1));
        assertEquals((byte)0b1111_1111, ret.get(2));
        assertEquals((byte)0, ret.get(3));
        assertEquals((byte)0, ret.get(4));
        assertEquals((byte)0b1000_0000, ret.get(5));
    }

    @Test
    public void testIntOverflowMisaligned() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(0, 2);
        writer.put(~0, 24);
        writer.put(0, 16);
        writer.put(1, 1);

        var ret = writer.finish();

        assertEquals(6, ret.capacity());
        assertEquals((byte)0b0011_1111, ret.get(0));
        assertEquals((byte)0b1111_1111, ret.get(1));
        assertEquals((byte)0b1111_1111, ret.get(2));
        assertEquals((byte)0b1100_0000, ret.get(3));
        assertEquals((byte)0, ret.get(4));
        assertEquals((byte)0b0010_0000, ret.get(5));
    }

    @Test
    public void testFuzzCase1() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(1, 6);
        writer.put(702, 11);

        var ret = writer.finish();

        var reader = new BitReader(ret);
        int a = reader.get(6);
        int b = reader.get(11);
        assertEquals(a, 1);
        assertEquals(b, 702);
    }

    @Test
    public void testFuzzCase2() {
        var buffer = ByteBuffer.allocate(1024);
        var writer = new BitWriter(buffer);

        writer.put(0, 6);
        writer.put(0, 2);

        var ret = writer.finish();

        assertEquals(1, ret.capacity());
        assertEquals(0, ret.get(0));

        var reader = new BitReader(ret);
        int a = reader.get(6);
        int b = reader.get(2);
        assertEquals(a, 0);
        assertEquals(b, 0);
    }

    @Test
    void fuzz() {
        Random r = new Random();

        for (int i = 0; i < 1000; i++) {
            var buffer = ByteBuffer.allocate(32);
            var writer = new BitWriter(buffer);
            int aw = r.nextInt(1, 31);
            int bw = r.nextInt(1, 31);
            int a = r.nextInt(0, 1<<aw - 1);
            int b = r.nextInt(0, 1<<bw - 1);
            System.out.println(a + "/" + aw + "," + b + "/" + bw);
            writer.put(a, aw);
            writer.put(b, bw);
            var ret = writer.finish();

            var reader = new BitReader(ret);
            int ra = reader.get(aw);
            int rb = reader.get(bw);

            assertEquals(a, ra);
            assertEquals(b, rb);
            System.out.println(a + "," + b);
        }
    }
}