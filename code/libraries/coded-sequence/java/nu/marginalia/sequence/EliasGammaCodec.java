package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.io.BitReader;
import nu.marginalia.sequence.io.BitWriter;

import java.nio.ByteBuffer;

/** Implement coding and decoding of sequences of integers using the Elias Gamma code
 *
 * https://en.wikipedia.org/wiki/Elias_gamma_coding
 * */
public class EliasGammaCodec implements IntIterator {

    private final BitReader reader;
    private int last = 0;
    private int next = 0;

    private EliasGammaCodec(ByteBuffer buffer) {
        reader = new BitReader(buffer);
    }

    /** Decode a sequence of integers from a ByteBuffer using the Elias Gamma code */
    public static IntIterator decode(ByteBuffer buffer) {
        return new EliasGammaCodec(buffer);
    }

    /** Encode a sequence of integers into a ByteBuffer using the Elias Gamma code.
     * The sequence must be strictly increasing and may not contain values less than
     * or equal to zero.
     */
    public static ByteBuffer encode(ByteBuffer workArea, IntList sequence) {
        var writer = new BitWriter(workArea);
        int last = 0;

        for (var iter = sequence.iterator(); iter.hasNext(); ) {
            int i = iter.nextInt();
            int delta = i - last;
            last = i;

            // can't encode zeroes
            assert delta > 0 : "Sequence must be strictly increasing and may not contain zeroes or negative values";

            int bits = Integer.numberOfTrailingZeros(Integer.highestOneBit(delta));
            writer.put(0, bits + 1);
            writer.put(delta, bits + 1);
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
        if (next > 0)
            return true;
        if (!reader.hasMore())
            return false;

        int bits = reader.takeWhileZero();

        if (!reader.hasMore()) {
            return false;
        }
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
