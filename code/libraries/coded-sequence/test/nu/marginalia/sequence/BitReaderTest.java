package nu.marginalia.sequence;

import nu.marginalia.sequence.io.BitReader;
import nu.marginalia.sequence.io.BitWriter;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class BitReaderTest {

    @Test
    void getBit() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));
        writer.putBit(true);
        writer.putBit(false);
        writer.put(0, 32);
        writer.putBit(true);
        writer.putBit(false);
        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        assertTrue(reader.getBit());
        assertFalse(reader.getBit());
        for (int i = 0; i < 32; i++) {
            assertFalse(reader.getBit());
        }
        assertTrue(reader.getBit());
        assertFalse(reader.getBit());
    }

    @Test
    void getInByte() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));

        writer.putBit(true);
        writer.putBit(false);

        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        int val = reader.get(2);
        assertEquals(0b10, val);
    }

    @Test
    void get() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));
        writer.putBit(true);
        writer.putBit(false);
        writer.put(0, 32);
        writer.putBit(true);
        writer.putBit(false);
        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        int val = reader.get(4);
        assertEquals(0b1000, val);

        val = reader.get(30);
        assertEquals(0b000, val);

        val = reader.get(2);
        assertEquals(0b10, val);
    }

    @Test
    void getSevens() {
        // Fuzz test that probes int32 misalignments
        var writer = new BitWriter(ByteBuffer.allocate(1024));

        for (int i = 0; i < 729; i++) {
            writer.putBit(true);
            writer.putBit(false);
            writer.putBit(false);
            writer.putBit(true);
            writer.putBit(false);
            writer.putBit(false);
            writer.putBit(true);
        }

        var buffer = writer.finish();

        var reader = new BitReader(buffer);

        for (int i = 0; i < 729; i++) {
            int val = reader.get(7);
            assertEquals(0b1001001, val);
        }
    }

    @Test
    public void testTakeWhileZero() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));
        writer.put(0, 4);
        writer.putBit(true);
        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        int val = reader.takeWhileZero();
        assertEquals(4, val);
        assertTrue(reader.getBit());
    }

    @Test
    public void testTakeWhileZeroAllZero() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));
        writer.put(0, 8);
        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        int val = reader.takeWhileZero();
        assertEquals(8, val);
    }

    @Test
    public void testTakeWhileZeroOverInt64() {
        var writer = new BitWriter(ByteBuffer.allocate(1024));
        writer.put(0, 32);
        writer.put(0, 32);
        writer.put(0, 2);
        writer.putBit(true);
        var buffer = writer.finish();

        var reader = new BitReader(buffer);
        int val = reader.takeWhileZero();
        assertEquals(66, val);
        assertTrue(reader.getBit());
    }
}