package nu.marginalia.sequence;

import blue.strategic.parquet.BinarySerializable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

/** A sequence of integers encoded using the Elias Gamma code,
 * the class wraps a ByteBuffer containing the encoded sequence,
 * and offers convenience methods for decoding and iterating
 * over the data.
 * */
public class GammaCodedSequence implements BinarySerializable, Iterable<Integer> {
    private final ByteBuffer raw;

    /** Create a new GammaCodedSequence from a sequence of integers.
     *
     * The sequence must be strictly increasing and may not contain
     * values less than or equal to zero.
     * */
    public static GammaCodedSequence generate(ByteBuffer workArea, int... values) {
        return new GammaCodedSequence(EliasGammaCodec.encode(workArea, values));
    }

    /** Create a new GammaCodedSequence from a sequence of integers.
     *
     * The sequence must be strictly increasing and may not contain
     * values less than or equal to zero.
     * */
    public static GammaCodedSequence generate(ByteBuffer workArea, IntList values) {
        return new GammaCodedSequence(EliasGammaCodec.encode(workArea, values));
    }

    public GammaCodedSequence(ByteBuffer bytes) {
        this.raw = bytes;
    }

    public GammaCodedSequence(byte[] bytes) {
        raw = ByteBuffer.allocate(bytes.length);
        raw.put(bytes);
        raw.clear();
    }

    /** Return the raw bytes of the sequence. */
    @Override
    public byte[] bytes() {
        if (raw.hasArray()) {
            return raw.array();
        }
        else {
            raw.clear();

            byte[] bytes = new byte[raw.capacity()];
            raw.get(bytes, 0, bytes.length);
            return bytes;
        }
    }

    @Override
    public IntIterator iterator() {
        raw.clear();

        return EliasGammaCodec.decode(raw);
    }

    /** Decode the sequence into an IntList;
     * this is a somewhat slow operation,
     * iterating over the data directly more performant */
    public IntList decode() {
        IntArrayList ret = new IntArrayList(8);
        var iter = iterator();
        while (iter.hasNext()) {
            ret.add(iter.nextInt());
        }
        return ret;
    }

    public int hashCode() {
        return raw.hashCode();
    }

    public boolean equals(Object obj) {
        return obj instanceof GammaCodedSequence other && Arrays.equals(bytes(), other.bytes());
    }

    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (Integer i : this) {
            sj.add(i.toString());
        }
        return sj.toString();
    }
}
