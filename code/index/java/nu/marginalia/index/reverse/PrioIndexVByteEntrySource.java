package nu.marginalia.index.reverse;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.EntrySource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/** Reads vbyte-compressed document IDs from the priority index (format version 2).
 * <p>
 * Compressed data is read in 4KB chunks and decompressed on demand,
 * avoiding large upfront allocations for high-frequency terms.
 */
public class PrioIndexVByteEntrySource implements EntrySource {
    private final String name;
    private final String term;

    private final int numItems;
    private int totalDecompressed = 0;

    // Compressed data reading
    private final FileChannel docsFileChannel;
    private long fileReadPos;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(4096).order(ByteOrder.nativeOrder());

    // Decompression state persisted across read() calls
    private long runningValue = 0;
    private long pendingControlWord = 0;
    private int pendingGroupRemaining = 0;

    // Worst case bytes for one group of 10: 4 (control) + 10*8 (values) = 84
    private static final int MAX_GROUP_BYTES = 84;

    public PrioIndexVByteEntrySource(String name,
                                     String term,
                                     FileChannel docsFileChannel,
                                     long dataOffsetStartB) {
        this.name = name;
        this.term = term;
        this.docsFileChannel = docsFileChannel;

        try {
            // Read 4-byte count prefix
            ByteBuffer countBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            docsFileChannel.read(countBuf, dataOffsetStartB);
            countBuf.flip();
            numItems = countBuf.getInt();

            fileReadPos = dataOffsetStartB + 4;

            // Start with an empty buffer; it will be filled on first read()
            readBuffer.position(0);
            readBuffer.limit(0);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read vbyte index data.", ex);
        }
    }

    @Override
    @SuppressWarnings("preview")
    public void read(LongQueryBuffer buffer) {
        int capacity = (int) buffer.data.size();
        int written = 0;

        // Resume a partially consumed group from the previous read() call
        while (pendingGroupRemaining > 0 && written < capacity && totalDecompressed < numItems) {
            int size = 1 + (int) (pendingControlWord & 0x7);
            runningValue += getFromBuffer(size);
            buffer.data.set(written++, runningValue);
            totalDecompressed++;
            pendingControlWord >>>= 3;
            pendingGroupRemaining--;
        }

        while (written < capacity && totalDecompressed < numItems) {
            ensureReadable();

            // Decompress one group of up to 10 values
            long controlWord = getFromBuffer(4);
            int groupSize = Math.min(10, numItems - totalDecompressed);

            for (int j = 0; j < groupSize; j++) {
                if (written >= capacity) {
                    // Save remaining group state for next read() call
                    pendingControlWord = controlWord;
                    pendingGroupRemaining = groupSize - j;
                    break;
                }

                int size = 1 + (int) (controlWord & 0x7);
                runningValue += getFromBuffer(size);
                buffer.data.set(written++, runningValue);
                totalDecompressed++;
                controlWord >>>= 3;
            }
        }

        buffer.end = written;
        buffer.uniq();
    }

    /** Ensure the read buffer has enough data for at least one full group. */
    private void ensureReadable() {
        if (readBuffer.remaining() >= MAX_GROUP_BYTES) {
            return;
        }

        try {
            readBuffer.compact();
            int bytesRead = docsFileChannel.read(readBuffer, fileReadPos);
            if (bytesRead > 0) {
                fileReadPos += bytesRead;
            }
            readBuffer.flip();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read compressed index data.", ex);
        }
    }

    /** Read a value of the given byte width from the read buffer (little-endian). */
    private long getFromBuffer(int bytes) {
        return switch (bytes) {
            case 1 -> readBuffer.get() & 0xFFL;
            case 2 -> readBuffer.getShort() & 0xFFFFL;
            case 3 -> {
                long low = readBuffer.getShort() & 0xFFFFL;
                long high = readBuffer.get() & 0xFFL;
                yield (low | (high << 16)) & 0x00FF_FFFFL;
            }
            case 4 -> readBuffer.getInt() & 0xFFFF_FFFFL;
            case 5 -> {
                long low = readBuffer.getInt() & 0xFFFF_FFFFL;
                long high = readBuffer.get() & 0xFFL;
                yield (low | (high << 32)) & 0x0000_00FF_FFFF_FFFFL;
            }
            case 6 -> {
                long low = readBuffer.getInt() & 0xFFFF_FFFFL;
                long high = readBuffer.getShort() & 0xFFFFL;
                yield (low | (high << 32)) & 0x0000_FFFF_FFFF_FFFFL;
            }
            case 7 -> {
                long low = readBuffer.get() & 0xFFL;
                long mid = readBuffer.getInt() & 0xFFFF_FFFFL;
                long high = readBuffer.getShort() & 0xFFFFL;
                yield (low | (mid << 8) | (high << 40)) & 0x00FF_FFFF_FFFF_FFFFL;
            }
            case 8 -> readBuffer.getLong();
            default -> throw new IllegalStateException("Unexpected byte size " + bytes);
        };
    }

    @Override
    public boolean hasMore() {
        return totalDecompressed < numItems;
    }

    @Override
    public String indexName() {
        return name + ":" + term;
    }

    @Override
    public int readEntries() {
        return totalDecompressed;
    }
}
