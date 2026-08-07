package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.sequence.VarintCodedSequence;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/** Decodes a ranking batch's varint coded position sequences with a single
 *  native call, so that a bulk copy per sequence is all that remains on the
 *  per document scoring path.  Falls back to a per sequence Java decode when
 *  the native library is unavailable.
 *  <p>
 *  An instance belongs to one ranking stage and is not thread safe.  Call
 *  decodeBatch once per batch, then positionsForDocument for each document.
 */
public class PositionsBatchDecoder {

    private static final boolean useNative = NativeAlgos.isAvailable
            && !Boolean.getBoolean("index.disableNativePositionsDecode");

    // Scratch, grown to the largest batch seen
    private long[] addrs = new long[512];
    private int[] lens = new int[512];
    private int[] counts = new int[512];
    private int[] offsets = new int[513];
    private int[] values = new int[1 << 16];
    private int[] docSeqStart = new int[65];

    /** Decode every position sequence in the batch into a shared value buffer */
    public void decodeBatch(MemorySegment[][] segments) {
        if (!useNative)
            return;

        int nSeqs = 0;
        long totalBytes = 0;

        for (MemorySegment[] docSegments : segments) {
            for (MemorySegment segment : docSegments) {
                if (segment != null) {
                    nSeqs++;
                    totalBytes += segment.byteSize();
                }
            }
        }

        if (addrs.length < nSeqs) {
            addrs = new long[nSeqs];
            lens = new int[nSeqs];
            counts = new int[nSeqs];
            offsets = new int[nSeqs + 1];
        }
        if (values.length < totalBytes) {
            values = new int[(int) totalBytes];
        }
        if (docSeqStart.length < segments.length + 1) {
            docSeqStart = new int[segments.length + 1];
        }

        int k = 0;
        for (int i = 0; i < segments.length; i++) {
            docSeqStart[i] = k;

            for (MemorySegment segment : segments[i]) {
                if (segment != null) {
                    addrs[k] = segment.address();
                    lens[k] = (int) segment.byteSize();
                    k++;
                }
            }
        }
        docSeqStart[segments.length] = k;

        NativeAlgos.decodeVarintBatch(addrs, lens, nSeqs, values, counts);

        offsets[0] = 0;
        for (int i = 0; i < nSeqs; i++) {
            offsets[i + 1] = offsets[i] + counts[i];
        }
    }

    /** The decoded position lists for one document of the batch passed to
     *  decodeBatch, copied into lists from the pool */
    public IntList[] positionsForDocument(MemorySegment[] docSegments, int docIdx, ScratchIntListPool pool) {
        IntList[] ret = new IntList[docSegments.length];

        if (!useNative) {
            for (int j = 0; j < docSegments.length; j++) {
                if (docSegments[j] == null) {
                    ret[j] = IntList.of();
                }
                else {
                    ByteBuffer buffer = docSegments[j].asByteBuffer();
                    ret[j] = new VarintCodedSequence(buffer, 0, buffer.capacity()).values(pool::get);
                }
            }
            return ret;
        }

        int flatIdx = docSeqStart[docIdx];

        for (int j = 0; j < docSegments.length; j++) {
            if (docSegments[j] == null) {
                ret[j] = IntList.of();
                continue;
            }

            int count = counts[flatIdx];
            int offset = offsets[flatIdx];
            flatIdx++;

            ScratchIntList list = pool.get(count);
            list.setSizeForOverwrite(count);
            System.arraycopy(values, offset, list.elements(), 0, count);
            ret[j] = list;
        }

        return ret;
    }
}
