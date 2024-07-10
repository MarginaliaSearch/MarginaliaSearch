package nu.marginalia.sequence;

import blue.strategic.parquet.BinarySerializable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.io.BitReader;
import nu.marginalia.sequence.io.BitWriter;

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

    private final int startPos;
    private final int startLimit;

    /** Create a new GammaCodedSequence from a sequence of integers.
     *
     * The sequence must be strictly increasing and may not contain
     * values less than or equal to zero.
     * */
    public static GammaCodedSequence generate(ByteBuffer workArea, int... values) {
        return new GammaCodedSequence(encode(workArea, values));
    }

    /** Create a new GammaCodedSequence from a sequence of integers.
     *
     * The sequence must be strictly increasing and may not contain
     * values less than or equal to zero.
     * */
    public static GammaCodedSequence generate(ByteBuffer workArea, IntList values) {
        return new GammaCodedSequence(encode(workArea, values));
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

        return new EliasGammaSequenceIterator(raw);
    }

    /** Return an iterator over the sequence with a constant offset applied to each value.
     * This is useful for comparing sequences with different offsets, and adds zero
     * extra cost to the decoding process which is already based on adding
     * relative differences.
     * */
    public IntIterator offsetIterator(int offset) {
        raw.position(startPos);
        raw.limit(startLimit);

        return new EliasGammaSequenceIterator(raw, offset);
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

    /** Return the backing ByteBuffer of the sequence, configured with a position and limit
     * that is equal to the relevant data range
     */
    public ByteBuffer buffer() {
        raw.position(startPos);
        raw.limit(startLimit);

        return raw;
    }

    /** Return the number of bytes used by the sequence in the buffer */
    public int bufferSize() {
        return startLimit - startPos;
    }

    /** Return the number of items in the sequence */
    public int valueCount() {
        if (startPos == startLimit)
            return 0;

        // if the first byte is zero, the sequence is empty and we can skip decoding
        if (0 == raw.get(startPos))
            return 0;

        return EliasGammaSequenceIterator.readCount(buffer());
    }


    /** Encode a sequence of integers into a ByteBuffer using the Elias Gamma code.
     * The sequence must be strictly increasing and may not contain values less than
     * or equal to zero.
     */
    public static ByteBuffer encode(ByteBuffer workArea, IntList sequence) {
        if (sequence.isEmpty())
            return ByteBuffer.allocate(0);

        var writer = new BitWriter(workArea);

        writer.putGamma(sequence.size());

        int last = 0;

        for (var iter = sequence.iterator(); iter.hasNext(); ) {
            int i = iter.nextInt();
            int delta = i - last;
            last = i;

            // can't encode zeroes
            assert delta > 0 : "Sequence must be strictly increasing and may not contain zeroes or negative values";

            writer.putGamma(delta);
        }

        // Finish the writer and return the work buffer, positioned and limited around
        // the relevant data

        var buffer = writer.finish();

        // Copy the contents of the writer's internal buffer to a new ByteBuffer that is correctly sized,
        // this lets us re-use the internal buffer for subsequent calls to encode without worrying about
        // accidentally overwriting the previous data.

        var outBuffer = ByteBuffer.allocate(buffer.limit());
        outBuffer.put(buffer);
        outBuffer.flip();

        return outBuffer;
    }

    /** Encode a sequence of integers into a ByteBuffer using the Elias Gamma code.
     * The sequence must be strictly increasing and may not contain values less than
     * or equal to zero.
     */
    public static ByteBuffer encode(ByteBuffer workArea, int[] sequence) {
        return encode(workArea, IntList.of(sequence));
    }

    /** Iterator that implements decoding of sequences of integers using the Elias Gamma code.
     *  The sequence is prefixed by the number of integers in the sequence, then the delta between
     *  each integer in the sequence is encoded using the Elias Gamma code.
     * <p></p>
     * <a href="https://en.wikipedia.org/wiki/Elias_gamma_coding">https://en.wikipedia.org/wiki/Elias_gamma_coding</a>
     * */
    public static class EliasGammaSequenceIterator implements IntIterator {

        private final BitReader reader;
        int rem = 0;
        private int last;
        private int next = 0;

        public EliasGammaSequenceIterator(ByteBuffer buffer, int zero) {
            reader = new BitReader(buffer);

            last = zero;
            int bits = 1 + reader.takeWhileZero();

            if (!reader.hasMore()) {
                rem = 0;
            }
            else {
                rem = reader.get(bits);
            }
        }

        public EliasGammaSequenceIterator(ByteBuffer buffer) {
            this(buffer, 0);
        }

        public static int readCount(ByteBuffer buffer) {
            var reader = new BitReader(buffer);

            int bits = 1 + reader.takeWhileZero();
            if (!reader.hasMore()) {
                return 0;
            }
            else {
                return reader.get(bits);
            }
        }



        // This is BitWriter.getGamma with more checks in place for streaming iteration
        @Override
        public boolean hasNext() {
            if (next > 0) return true;
            if (!reader.hasMore() || --rem < 0) return false;

            int bits = 1 + reader.takeWhileZero();

            if (reader.hasMore()) {
                int delta = reader.get(bits);
                last += delta;
                next = last;

                return true;
            }

            return false;
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
}
