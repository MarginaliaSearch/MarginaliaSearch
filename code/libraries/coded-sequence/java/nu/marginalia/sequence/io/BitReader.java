package nu.marginalia.sequence.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/** A utility class for reading bits from a ByteBuffer
 * out of alignment with octet boundaries
 */
public class BitReader {
    private final ByteBuffer underlying;
    private final Runnable refillCallback;

    private static final Logger logger = LoggerFactory.getLogger(BitReader.class);

    /** The current value being decoded */
    private long currentValue;

    /** Bit index in the current value */
    private int bitPosition;


    /** Create a new BitReader for the given buffer.  The supplied callback will be
     * invoked when the underlying buffer is out of data.  The callback should
     * refill the buffer with more data.
     */
    public BitReader(ByteBuffer buffer, Runnable refillCallback) {
        this.underlying = buffer;
        this.refillCallback = refillCallback;
        this.bitPosition = 0;
        this.currentValue = 0;
    }

    /** Create a new BitReader for the given buffer */
    public BitReader(ByteBuffer buffer) {
        this(buffer, () -> { throw new IllegalStateException("No more data to read and no re-fill callback provided"); });
    }

    /** Read the next bit from the buffer */
    public boolean getBit() {
        if (bitPosition <= 0) {
            readNext();
        }

        // Return the bit at the current position, then decrement the position
        return (currentValue & (1L << (--bitPosition))) != 0;
    }

    /** Read the next width bits from the buffer */
    public int get(int width) {
        if (width == 0)
            return 0;

        if (bitPosition <= 0) {
            readNext();
        }

        int result = 0;

        while (width > 0) {
            int dw = bitPosition - width;

            if (dw >= 0) { // We have enough bits in the current value to satisfy the request
                result |= ((int)(currentValue >>> dw)) & ~-(1<<width);

                // Update the bit position
                bitPosition -= width;

                // We've read all the bits we need
                width = 0;
            } else { // We need to split the value between two successive integers

                // Extract the remaining bits from the current value to the result
                // and shift them to the left to leave room for the bits still to be read
                result |= (int)((currentValue & ~-(1L<<bitPosition)) << -dw);

                // Decrement the number of bits left to read by the number of bits read
                // so that we read the remainder as we loop around
                width -= bitPosition;

                // Read the next integer
                readNext(); // implicitly: bitPosition = 0 here
            }
        }

        return result;
    }

    /** Read bits until a 1 is encountered */
    public int takeWhileZero() {
        int result = 0;

        do {
            // Ensure we have bits to read
            if (bitPosition <= 0) {
                if (underlying.hasRemaining())
                    readNext();
                else break;
            }

            // Count the number of leading zeroes in the current value
            int zeroes = Long.numberOfLeadingZeros(currentValue << (64 - bitPosition));

            // Add the number of zeroes to the result, but cap it at the
            // current bit position to avoid counting padding bits as zeroes
            result += Math.min(bitPosition, zeroes);

            // Subtract the number of bits read from the current position
            bitPosition -= zeroes;

            // If bit position is not positive, we've found a 1 and can stop
        } while (bitPosition <= 0);

        return result;
    }

    public int getGamma() {
        int bits = takeWhileZero();
        return get(bits + 1);
    }

    public int getDelta() {
        int bits = getGamma();
        return get(bits);
    }

    public boolean hasMore() {
        return bitPosition > 0 || underlying.hasRemaining();
    }

    private void readNext() {
        int remainingCapacity = underlying.remaining();

        if (remainingCapacity >= 8) {
            currentValue = underlying.getLong();
            bitPosition = 64;
        }
        else if (remainingCapacity >= 4) {
            currentValue = underlying.getInt() & 0xFFFF_FFFFL;
            bitPosition = 32;
        }
        else if (remainingCapacity >= 2) {
            currentValue = underlying.getShort() & 0xFFFF;
            bitPosition = 16;
        }
        else if (remainingCapacity == 1) {
            currentValue = underlying.get() & 0xFF;
            bitPosition = 8;
        }
        else { // There's no more data to read!
            refillCallback.run();
            readNext();
        }
    }
}
