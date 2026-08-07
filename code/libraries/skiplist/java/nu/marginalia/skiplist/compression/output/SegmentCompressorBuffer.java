package nu.marginalia.skiplist.compression.output;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SegmentCompressorBuffer implements ReadableCompressorBufferIf {
    private final MemorySegment segment;
    private long pos;

    public SegmentCompressorBuffer(MemorySegment segment, long pos) {
        this.segment = segment;
        this.pos = pos;
    }

    public MemorySegment segment() {
        return segment;
    }

    public long getPos() {
        return pos;
    }
    public void setPos(long pos) {
        this.pos = pos;
    }
    public void advancePos(int n) {
        this.pos += n;
    }

    private static final long[] VALUE_MASKS = {
            0L,
            0xFFL,
            0xFFFFL,
            0x00FF_FFFFL,
            0xFFFF_FFFFL,
            0x0000_00FF_FFFF_FFFFL,
            0x0000_FFFF_FFFF_FFFFL,
            0x00FF_FFFF_FFFF_FFFFL,
            ~0L
    };

    public long get(int bytes) {
        // Reading a full long and masking costs a single bounds and liveness check instead of up to three

        if (pos + 8 <= segment.byteSize()) {
            long ret = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, pos) & VALUE_MASKS[bytes];
            advancePos(bytes);
            return ret;
        }

        // fallback near the segment boundary
        long ret = switch (bytes) {
            case 1 -> segment.get(ValueLayout.JAVA_BYTE, pos) & 0xFFL;
            case 2 -> segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, pos) & 0xFFFFL;
            case 3 -> {
                long low = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, pos) & 0xFFFFL;
                long high = segment.get(ValueLayout.JAVA_BYTE, pos+2) & 0xFFL;
                yield (low | (high << 16)) & 0x00FF_FFFFL;
            }
            case 4 -> segment.get(ValueLayout.JAVA_INT_UNALIGNED, pos) & 0xFFFF_FFFFL;
            case 5 -> {
                long low = segment.get(ValueLayout.JAVA_INT_UNALIGNED, pos) & 0xFFFF_FFFFL;
                long high = segment.get(ValueLayout.JAVA_BYTE, pos+4) & 0xFFL;
                yield (low | (high << 32)) & 0x0000_00FF_FFFF_FFFFL;
            }
            case 6 -> {
                long low = segment.get(ValueLayout.JAVA_INT_UNALIGNED, pos) & 0xFFFF_FFFFL;
                long high =segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, pos + 4) & 0xFFFFL;
                yield (low | (high << 32)) & 0x0000_FFFF_FFFF_FFFFL;
            }
            case 7 -> {
                long low = segment.get(ValueLayout.JAVA_BYTE, pos) & 0xFF;
                long mid = segment.get(ValueLayout.JAVA_INT_UNALIGNED, pos+1) & 0xFFFF_FFFFL;
                long high = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, pos + 5) & 0xFFFFL;
                yield (low | (mid << 8) | (high << 40)) & 0x00FF_FFFF_FFFF_FFFFL;
            }
            case 8 -> segment.get(ValueLayout.JAVA_LONG_UNALIGNED, pos);
            default -> throw new UnsupportedOperationException("Unexpected byte size " + bytes);
        };
        advancePos(bytes);
        return ret;
    }

}
