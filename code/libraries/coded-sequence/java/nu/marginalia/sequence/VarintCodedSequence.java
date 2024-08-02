package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;

public class VarintCodedSequence implements CodedSequence {

    private final ByteBuffer raw;

    private final int startPos;
    private final int startLimit;

    public VarintCodedSequence(ByteBuffer buffer) {
        this.raw = buffer;

        this.startPos = buffer.position();
        this.startLimit = buffer.limit();
    }

    private static int requiredBufferSize(int[] values) {
        int prev = 0;
        int size = 0;

        for (int value : values) {
            size += varintSize(value - prev);
            prev = value;
        }

        return size + varintSize(size + 1);
    }

    private static int varintSize(int value) {
        int bits = 32 - Integer.numberOfLeadingZeros(value);
        return (bits + 6) / 7;
    }

    public static VarintCodedSequence generate(int... values) {
        int bufferSize = requiredBufferSize(values);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        int prev = 0;

        encodeValue(buffer, values.length + 1);

        for (int value : values) {
            int toEncode = value - prev;
            assert toEncode > 0 : "Values must be strictly increasing";

            encodeValue(buffer, toEncode);

            prev = value;
        }

        buffer.flip();

        return new VarintCodedSequence(buffer);
    }

    private static void encodeValue(ByteBuffer buffer, int value) {
        if (value < 0x80) {
            buffer.put((byte) value);
        }
        else if (value < 0x4_000) {
            buffer.put((byte) (value >>> (7) | 0x80));
            buffer.put((byte) (value & 0x7F));
        }
        else if (value < 0x20_0000) {
            buffer.put((byte) (value >>> (14) | 0x80));
            buffer.put((byte) (value >>> (7) | 0x80));
            buffer.put((byte) (value & 0x7F));
        }
        else if (value < 0x1000_0000) {
            buffer.putInt(Integer.expand(value, 0x00808080) | 0x80808000);
        }
        else {
            throw new IllegalArgumentException("Value too large to encode");
        }
    }

    @Override
    public byte[] bytes() {
        return raw.array();
    }

    @Override
    public IntIterator iterator() {
        return new VarintSequenceIterator(buffer());
    }

    @Override
    public IntIterator offsetIterator(int offset) {
        return new VarintSequenceIterator(buffer(), offset);
    }

    @Override
    public IntList values() {
        var buffer = buffer();

        int val = 0;
        int count = decodeValue(buffer) - 1;

        IntArrayList list = new IntArrayList(count);

        while (buffer.hasRemaining()) {
            val += decodeValue(buffer);
            list.add(val);
        }

        return list;
    }

    @Override
    public ByteBuffer buffer() {
        raw.position(startPos);
        raw.limit(startLimit);

        return raw;
    }

    @Override
    public int bufferSize() {
        return raw.capacity();
    }

    @Override
    public int valueCount() {
        var buffer = buffer();
        return decodeValue(buffer) - 1;
    }

    private static int decodeValue(ByteBuffer buffer) {
        // most common case gets a fast path, this is a fairly large performance win
        // on average, something like 10-20% faster than not having this check
        byte b = buffer.get();
        if ((b & 0x80) == 0) {
            return b;
        }

        int value = b;
        do {
            b = buffer.get();
            value = value << 7 | (b & 0x7F);
        } while ((b & 0x80) != 0);

        return value;
    }

    public static class VarintSequenceIterator implements IntIterator {

        private final ByteBuffer buffer;
        int rem = 0;
        private int last;
        private int next = Integer.MIN_VALUE;

        public VarintSequenceIterator(ByteBuffer buffer, int zero) {
            this.buffer = buffer;
            if (zero == Integer.MIN_VALUE) {
                throw new IllegalArgumentException("Integer.MIN_VALUE is a reserved offset that may not be used as zero point");
            }

            last = zero;
            rem = decodeValue(buffer) - 1;
        }

        public VarintSequenceIterator(ByteBuffer buffer) {
            this(buffer, 0);
        }

        // This is BitWriter.getGamma with more checks in place for streaming iteration
        @Override
        public boolean hasNext() {
            if (next != Integer.MIN_VALUE) return true;
            if (--rem < 0) return false;

            int delta = decodeValue(buffer);

            last += delta;
            next = last;

            return true;
        }

        @Override
        public int nextInt() {
            if (hasNext()) {
                int ret = next;
                next = Integer.MIN_VALUE;
                return ret;
            }
            throw new ArrayIndexOutOfBoundsException("No more data to read");
        }


    }
}
