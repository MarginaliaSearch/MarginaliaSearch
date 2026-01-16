package nu.marginalia.skiplist.compression.output;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CompressorBuffer {
    private final ByteBuffer buffer;

    public CompressorBuffer(ByteBuffer buffer) {
        buffer.order(ByteOrder.nativeOrder());

        this.buffer = buffer;
    }

    public int getPos() {
        return buffer.position();
    }
    public void setPos(int pos) {
        buffer.position(pos);
    }
    public void advancePos(int n) {
        buffer.position(buffer.position() + n);
    }

    public void put(long val, int bytes) {
        switch (bytes) {
            case 1 -> buffer.put((byte) (val & 0xFFL));
            case 2 -> buffer.putShort((short) val);
            case 3 -> {
                buffer.put((byte) (val & 0xFFL));
                buffer.putShort((short) ((val >>> 8) & 0xFFFFL));
            }
            case 4 -> buffer.putInt((int) val);
            case 5 -> {
                buffer.put((byte) (val & 0xFFL));
                buffer.putInt( (int) ((val >>> 8) & 0xFFFF_FFFFL));
            }
            case 6 -> {
                buffer.putShort((short) (val & 0xFFFFL));
                buffer.putInt((int) ((val >> 16) & 0xFFFF_FFFFL));
            }
            case 7 -> {
                buffer.put((byte) (val & 0xFFL));
                buffer.putShort((short) ((val >> 8) & 0xFFFFL));
                buffer.putInt((int) ((val >> 24) & 0xFFFF_FFFFL));
            }
            case 8 -> buffer.putLong(val);
        }
    }

    public long get(int bytes) {
        return switch (bytes) {
            case 1 -> buffer.get() & 0xFFL;
            case 2 -> buffer.getShort() & 0xFFFFL;
            case 3 -> {
                long low = buffer.getShort() & 0xFFFFL;
                long high = buffer.get() & 0xFFL;
                yield (low | (high << 16)) & 0x00FF_FFFFL;
            }
            case 4 -> buffer.getInt() & 0xFFFF_FFFFL;
            case 5 -> {
                long low = buffer.getInt() & 0xFFFF_FFFFL;
                long high = buffer.get() & 0xFFL;
                yield (low | (high << 32)) & 0x0000_00FF_FFFF_FFFFL;
            }
            case 6 -> {
                long low = buffer.getInt() & 0xFFFF_FFFFL;
                long high = buffer.getShort() & 0xFFFFL;
                yield (low | (high << 32)) & 0x0000_FFFF_FFFF_FFFFL;
            }
            case 7 -> {
                long low = buffer.get() & 0xFF;
                long mid = buffer.getInt() & 0xFFFF_FFFFL;
                long high = buffer.getShort() & 0xFFFFL;
                yield (low | (mid << 8) | (high << 40)) & 0x00FF_FFFF_FFFF_FFFFL;
            }
            case 8 -> buffer.getLong();
            default -> throw new UnsupportedOperationException("Unexpected byte size " + bytes);
        };
    }

    public void padToLong() {
        // listen, I wouldn't worry about it
        put(0, (8 - buffer.position() & 7) & 7);
    }

}
