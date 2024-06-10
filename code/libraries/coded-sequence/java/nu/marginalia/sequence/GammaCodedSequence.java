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

    int startPos = 0;
    int startLimit = 0;

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
        startPos = bytes.position();
        startLimit = bytes.limit();
    }

    public GammaCodedSequence(ByteBuffer bytes, int startPos, int startLimit) {
        this.raw = bytes;
        this.startPos = startPos;
        this.startLimit = startLimit;
    }

    public GammaCodedSequence(byte[] bytes) {
        raw = ByteBuffer.allocate(bytes.length);
        raw.put(bytes);
        raw.clear();
        startPos = 0;
        startLimit = bytes.length;
    }

    /** Return the raw bytes of the sequence. */
    @Override
    public byte[] bytes() {
        if (raw.hasArray()) {
            return raw.array();
        }
        else {
            byte[] bytes = new byte[raw.capacity()];
            raw.get(0, bytes, 0, bytes.length);
            return bytes;
        }
    }

    @Override
    public IntIterator iterator() {
        raw.position(startPos);
        raw.limit(startLimit);

        return EliasGammaCodec.decode(raw);
    }

    /** Return an iterator over the sequence with a constant offset applied to each value.
     * This is useful for comparing sequences with different offsets, and adds zero
     * extra cost to the decoding process which is already based on adding
     * relative differences.
     * */
    public IntIterator offsetIterator(int offset) {
        raw.position(startPos);
        raw.limit(startLimit);

        return EliasGammaCodec.decodeWithOffset(raw, offset);
    }

    public IntList values() {
        var intItr = iterator();
        IntArrayList ret = new IntArrayList(8);
        while (intItr.hasNext()) {
            ret.add(intItr.nextInt());
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

    public ByteBuffer buffer() {
        raw.position(startPos);
        raw.limit(startLimit);

        return raw;
    }

    public int bufferSize() {
        return raw.capacity();
    }

    public int valueCount() {
        return EliasGammaCodec.readCount(buffer());
    }
}
