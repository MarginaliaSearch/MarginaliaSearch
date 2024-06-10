package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.io.BitReader;
import nu.marginalia.sequence.io.BitWriter;

import java.nio.ByteBuffer;

/** Implement coding and decoding of sequences of integers using the Elias Gamma code.
 *  The sequence is prefixed by the number of integers in the sequence, then the delta between
 *  each integer in the sequence is encoded using the Elias Gamma code.
 * <p></p>
 * <a href="https://en.wikipedia.org/wiki/Elias_gamma_coding">https://en.wikipedia.org/wiki/Elias_gamma_coding</a>
 * */
public class EliasGammaCodec implements IntIterator {

    private final BitReader reader;
    int rem = 0;
    private int last;
    private int next = 0;

    private EliasGammaCodec(ByteBuffer buffer, int zero) {
        reader = new BitReader(buffer);

        last = zero;
        int bits = reader.takeWhileZero();

        if (!reader.hasMore()) {
            rem = 0;
        }
        else {
            rem = reader.get(bits);
        }
    }

    public static int readCount(ByteBuffer buffer) {
        var reader = new BitReader(buffer);

        if (reader.getCurrentValue() > 0) {
            int bits = reader.takeWhileZero();
            return reader.get(bits);
        }
        else {
            return 0;
        }
    }

    /** Decode a sequence of integers from a ByteBuffer using the Elias Gamma code */
    public static IntIterator decode(ByteBuffer buffer) {
        return new EliasGammaCodec(buffer, 0);
    }
    public static IntIterator decodeWithOffset(ByteBuffer buffer, int offset) {
        return new EliasGammaCodec(buffer, offset);
    }

    /** Encode a sequence of integers into a ByteBuffer using the Elias Gamma code.
     * The sequence must be strictly increasing and may not contain values less than
     * or equal to zero.
     */
    public static ByteBuffer encode(ByteBuffer workArea, IntList sequence) {
        if (sequence.isEmpty())
            return ByteBuffer.allocate(0);

        var writer = new BitWriter(workArea);

        writer.putGammaCoded(sequence.size());

        int last = 0;

        for (var iter = sequence.iterator(); iter.hasNext(); ) {
            int i = iter.nextInt();
            int delta = i - last;
            last = i;

            // can't encode zeroes
            assert delta > 0 : "Sequence must be strictly increasing and may not contain zeroes or negative values";

            writer.putGammaCoded(delta);
        }

        return writer.finish();
    }

    /** Encode a sequence of integers into a ByteBuffer using the Elias Gamma code.
     * The sequence must be strictly increasing and may not contain values less than
     * or equal to zero.
     */
    public static ByteBuffer encode(ByteBuffer workArea, int[] sequence) {
        return encode(workArea, IntList.of(sequence));
    }

    @Override
    public boolean hasNext() {
        if (next > 0) return true;
        if (!reader.hasMore() || --rem < 0) return false;

        int bits = reader.takeWhileZero();

        if (!reader.hasMore()) return false;
        
        int delta = reader.get(bits);
        last += delta;
        next = last;

        return true;
    }

    @Override
    public int nextInt() {
        if (hasNext()) {
            int ret = next;
            next = -1;
            return ret;
        }
        throw new ArrayIndexOutOfBoundsException("No more data to read");
    }


}
