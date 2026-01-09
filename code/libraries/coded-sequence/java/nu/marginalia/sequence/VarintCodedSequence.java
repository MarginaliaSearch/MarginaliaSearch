package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;
import java.util.Objects;

public class VarintCodedSequence implements CodedSequence {

    private final ByteBuffer raw;

    private final int startPos;
    private final int startLimit;

    public VarintCodedSequence(ByteBuffer buffer) {
        this.raw = buffer;

        this.startPos = buffer.position();
        this.startLimit = buffer.limit();
    }

    public VarintCodedSequence(ByteBuffer buffer, int startPos, int startLimit) {
        this.raw = buffer;

        this.startPos = startPos;
        this.startLimit = startLimit;
    }

    public static VarintCodedSequence generate(IntList values) {
        int bufferSize = requiredBufferSize(values);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        int prev = 0;

        encodeValue(buffer, values.size() + 1);

        for (int i = 0; i < values.size(); i++) {
            int value = values.getInt(i);
            int toEncode = value - prev;
            assert toEncode > 0 : "Values must be strictly increasing";

            encodeValue(buffer, toEncode);

            prev = value;
        }

        buffer.flip();

        return new VarintCodedSequence(buffer);
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

    /** Calculate the number of bytes required to encode a sequence of values as a varint. */
    private static int requiredBufferSize(int[] values) {
        int prev = 0;
        int size = 0;

        for (int value : values) {
            size += varintSize(value - prev);
            prev = value;
        }

        return size + varintSize(size + 1);
    }

    /** Calculate the number of bytes required to encode a sequence of values as a varint. */
    private static int requiredBufferSize(IntList values) {
        int prev = 0;
        int size = 0;

        for (int i = 0; i < values.size(); i++) {
            int value = values.getInt(i);
            size += varintSize(value - prev);
            prev = value;
        }

        return size + varintSize(size + 1);
    }

    /** Calculate the number of bytes required to encode a value as a varint. */
    private static int varintSize(int value) {
        int bits = 32 - Integer.numberOfLeadingZeros(value);
        return (bits + 6) / 7;
    }

    private static void encodeValue(ByteBuffer buffer, int value) {
        if (value < (1<<7)) {
            buffer.put((byte) value);
        }
        else if (value < (1<<14)) {
            buffer.put((byte) (value >>> (7) | 0x80));
            buffer.put((byte) (value & 0x7F));
        }
        else if (value < (1<<21)) {
            buffer.put((byte) (value >>> (14) | 0x80));
            buffer.put((byte) (value >>> (7) | 0x80));
            buffer.put((byte) (value & 0x7F));
        }
        else if (value < (1<<28)) {
            buffer.put((byte) ((value >>> 21) | 0x80));
            buffer.put((byte) ((value >>> 14) | 0x80));
            buffer.put((byte) ((value >>> 7) | 0x80));
            buffer.put((byte) (value & 0x7F));
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
        return new VarintSequenceIterator(raw, startPos);
    }

    @Override
    public IntIterator offsetIterator(int offset) {
        return new VarintSequenceIterator(raw, startPos, offset);
    }

    public IntList values() {
        return values(IntArrayList::new);
    }

    @Override
    public IntList values(Int2ObjectFunction<IntArrayList> allocator) {
        var buffer = buffer();

        int val = 0;
        int count = decodeValue(buffer) - 1;

        IntArrayList list = allocator.get(count);

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

        int value = b & 0x7F;
        do {
            b = buffer.get();
            value = (value << 7) | (b & 0x7F);
        } while ((b & 0x80) != 0);

        return value;
    }

    public static class VarintSequenceIterator implements IntIterator {

        private final ByteBuffer buffer;

        // The position in the buffer where the next value is read from,
        // we don't use the buffer's position, because we might want to access
        // this buffer from multiple iterators simultaneously without interference.
        private int bufferPos;

        /** The number of values remaining to be decoded from the buffer. */
        private int numRemainingValues;

        /** The previous value that was read from the buffer,
         * used in differential decoding. */
        private int previousValue;

        /** The next value that will be returned by nextInt,
         * set to MIN_VALUE if no value is yet decoded */
        private int nextValue = Integer.MIN_VALUE;

        /** Create a new VarintSequenceIterator from a buffer.
         * <p></p>
         * The iterator will start at the given position in the buffer.
         * The zero point is added to each value being read from the buffer.
         * */
        public VarintSequenceIterator(ByteBuffer buffer,
                                      int startPos,
                                      int zero) {
            this.buffer = buffer;
            if (zero == Integer.MIN_VALUE) {
                throw new IllegalArgumentException("Integer.MIN_VALUE is a reserved offset that may not be used as zero point");
            }

            bufferPos = startPos;

            previousValue = zero;
            numRemainingValues = decodeValue() - 1;
        }

        /** Create a new VarintSequenceIterator from a buffer.
         * <p></p>
         * The iterator will start at the given position in the buffer.
         * The zero point is 0.
         * */
        public VarintSequenceIterator(ByteBuffer buffer, int startPos) {
            this(buffer, startPos, 0);
        }

        // This is BitWriter.getGamma with more checks in place for streaming iteration
        @Override
        public boolean hasNext() {
            if (nextValue != Integer.MIN_VALUE) return true;
            if (--numRemainingValues < 0) return false;

            int delta = decodeValue();

            previousValue += delta;
            nextValue = previousValue;

            return true;
        }

        // This is the same operation as decodeValue in the outer class,
        // except we don't use the buffer's inbuilt position().
        private int decodeValue() {
            byte b = buffer.get(bufferPos++);
            if ((b & 0x80) == 0) {
                return b;
            }

            int value = b & 0x7F;
            do {
                b = buffer.get(bufferPos++);
                value = (value << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);

            return value;
        }

        @Override
        public int nextInt() {
            if (hasNext()) {
                int ret = nextValue;
                nextValue = Integer.MIN_VALUE;
                return ret;
            }
            throw new ArrayIndexOutOfBoundsException("No more data to read");
        }


    }

    public int hashCode() {
        return values().hashCode();
    }

    public boolean equals(Object other) {
        if (other instanceof CodedSequence cs) {
            return Objects.equals(values(), cs.values());
        }
        return false;
    }
}
