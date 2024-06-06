package nu.marginalia.sequence.io;

import java.nio.ByteBuffer;

/** A utility class for writing bits to a ByteBuffer
 * out of alignment with octet boundaries
 */
public class BitWriter {
    private final ByteBuffer underlying;

    /** The current value being encoded */
    private long currentValue;
    /** Bit index in the current value */
    private int bitPosition;

    /** The total number of significant bytes that have been written to the buffer,
     * the actual number of bytes may be larger than this value, but the trailing
     * values should be ignored */
    private int totalMeaningfulBytes;

    public BitWriter(ByteBuffer workBuffer) {
        this.underlying = workBuffer;
        this.bitPosition = 0;
        this.currentValue = 0;
        this.totalMeaningfulBytes = 0;

        underlying.clear();
    }

    public void putBit(boolean value) {
        if (value) {
            currentValue = 1 | (currentValue << 1);
        }
        else {
            currentValue <<= 1;
        }

        // If we've exceeded the integer size, write it to the buffer
        // and start over with the next integer

        if (++bitPosition == 64) {
            underlying.putLong(currentValue);
            totalMeaningfulBytes+=8;

            bitPosition = 0;
            currentValue = 0;
        }
    }

    /** Write the lowest width bits of the value to the buffer */
    public void put(int value, int width) {
        assert width <= 32 : "Attempting to write more than 32 bits from a single integer";

        int rem = (64 - bitPosition);

        if (rem < width) { // The value is split between two integers
            // write the first part of the byte
            currentValue = (currentValue << rem) | (value >>> (width - rem));

            // switch to the next integer
            underlying.putLong(currentValue);
            totalMeaningfulBytes+=8;

            // write the remaining part to currentValue
            currentValue = value & ((1L << (width - rem)) - 1);
            bitPosition = width - rem;
        }
        else { // The entire value fits in the current integer
            currentValue <<= width;
            currentValue |= (value & ((1L << width) - 1));
            bitPosition += width;
        }
    }

    /** Write the provided value in a gamma-coded format,
     * e.g. by first finding the number of significant bits,
     * then writing that many zeroes, then the bits themselves
     */
    public void putGammaCoded(int value) {
        int bits = 1 + Integer.numberOfTrailingZeros(Integer.highestOneBit(value));

        put(0, bits);
        put(value, bits);
    }

    public ByteBuffer finish() {
        finishLastByte();

        var outBuffer = ByteBuffer.allocate(totalMeaningfulBytes);

        outBuffer.put(underlying.array(), 0, totalMeaningfulBytes);

        outBuffer.position(0);
        outBuffer.limit(totalMeaningfulBytes);

        return outBuffer;
    }

    public ByteBuffer finish(ByteBuffer outBuffer) {
        finishLastByte();

        outBuffer.put(underlying.array(), 0, totalMeaningfulBytes);

        outBuffer.position(0);
        outBuffer.limit(totalMeaningfulBytes);

        return outBuffer;
    }

    private void finishLastByte() {
        // It's possible we have a few bits left over that have yet to be written
        // to the underlying buffer. We need to write them out now.

        if (bitPosition > 0) {
            totalMeaningfulBytes += bitPosition / 8 + ((bitPosition % 8 == 0) ? 0 : 1);
            underlying.putLong(currentValue << (64 - bitPosition));
        }

        // Reset the bit position to reflect that we've written the last byte
        bitPosition = 0;
    }

}
