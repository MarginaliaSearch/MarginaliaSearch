package nu.marginalia.skiplist.compression;

import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.skiplist.compression.input.CompressorInput;
import nu.marginalia.skiplist.compression.output.ReadableCompressorBufferIf;
import nu.marginalia.skiplist.compression.output.SegmentCompressorBuffer;
import nu.marginalia.skiplist.compression.output.WritableCompressorBufferIf;

/** Document ID compressor that encodes 10 variable-byte values with a single 32 bit control word,
 * allowing for 3 bits per value to encode their byte sizes with (1-8).
 * */
public class DocIdCompressor {

    /** Given a max capacity, calculate how much of the input array would fit */
    public static int calcMaxEntries(CompressorInput input, int capacity) {
        int requiredSize = 0;

        int nItems = 0;

        final int n = input.size();

        long prev = 0;

        for (int i = 0; i < n;) {
            int itemsInChunk = 0;

            for (int j = 0; j < 10 && i < n; j++, i++) {
                long current = input.at(i);
                long delta = current - prev;
                prev = current;

                requiredSize += numBytesRequired(delta);
                itemsInChunk++;
            }

            requiredSize += 4; // control word

            if (requiredSize > capacity)
                return nItems;

            nItems += itemsInChunk;
        }

        return nItems;
    }

    /** Compress at most n items from the give input, into the provided output
     *
     * @return the number of bytes written
     * */
    public static long compress(CompressorInput input, int n, WritableCompressorBufferIf output) {
        assert n <= input.size();

        final long offsetStart = output.getPos();

        final byte[] sizes = new byte[10];

        long prev = 0;

        int i = 0;
        while (i < n) {

            // save space for control word
            long reservePos = output.getPos();
            output.advancePos(4);

            int j;
            for (j = 0; j < 10 && i < n; j++, i++) {
                long current = input.at(i);
                long delta = current - prev;
                prev = current;

                sizes[j] = (byte) (numBytesRequired(delta));
                output.put(delta, sizes[j]);
            }

            long endPos = output.getPos();
            output.setPos(reservePos);
            output.put(encodeControlWord(sizes, j), 4);
            output.setPos(endPos);
        }

        return output.getPos() - offsetStart;
    }

    /** Decompress n items from the provided input to the array */
    public static void decompress(SegmentCompressorBuffer input, int n, long[] output) {
        int outIdx = 0;
        long val = 0L;
        while (outIdx < n) {
            long controlWord = input.get(4);
            for (int j = 0; j < 10 && outIdx < n; j++, outIdx++) {
                int size = 1 + (int) (controlWord & 0x7);

                val += input.get(size);
                output[outIdx] = val;

                controlWord >>>= 3;
            }
        }
    }

    /** Decompress n items from the provided input to the LongList */
    public static void decompress(ReadableCompressorBufferIf input, int n, LongList output) {
        int outIdx = 0;
        long val = 0L;

        while (outIdx < n) {
            long controlWord = input.get(4);
            for (int j = 0; j < 10 && outIdx < n; j++, outIdx++) {
                int size = 1 + (int) (controlWord & 0x7);

                val += input.get(size);
                output.add(val);

                controlWord >>>= 3;
            }
        }
    }

    private static long encodeControlWord(byte[] sizes, int j) {
        long ret = 0;
        for (int i = 0; i < j; i++) {
            ret |= ((long) (sizes[i] - 1) << 3*i);
        }
        return ret;
    }

    public static int numBytesRequired(long value) {
        return Math.max(1, (64 - Long.numberOfLeadingZeros(value) + 7) / 8);
    }
}
