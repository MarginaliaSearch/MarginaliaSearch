package nu.marginalia.btree.paged;

import nu.marginalia.btree.BTreeWriterIf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes a B+-tree to a file.
 * <p>
 * File layout:
 * <pre>
 *   Page 0: File header
 *   Pages 1..N: Leaf pages (sorted entries)
 *   Pages N+1..M: Internal pages (separator keys + child offsets)
 *   The root page is the last page written.
 * </pre>
 * <p>
 * Header page layout (all values little-endian):
 * <pre>
 *   int: magic number (0x42545245 = "BTRE")
 *   int: format version (currently 1)
 *   int: page size in bytes
 *   int: entry size (longs per entry)
 *   int: number of entries
 *   long: root page offset (byte offset from start of file)
 *   int: tree height (1 = leaves only)
 * </pre>
 * <p>
 * Leaf page layout:
 * <pre>
 *   int: number of entries in this page
 *   int: flags (0x01 = leaf)
 *   long[]: entries, each entrySize longs
 * </pre>
 * <p>
 * Internal page layout:
 * <pre>
 *   int: number of keys in this page
 *   int: flags (0x02 = internal)
 *   long[numKeys]: separator keys (max key in left subtree)
 *   long[numKeys+1]: child page offsets
 * </pre>
 */
public class PagedBTreeWriter implements BTreeWriterIf {

    public static final int MAGIC = 0x42545245; // "BTRE"
    public static final int FORMAT_VERSION = 2;
    static final int FLAG_LEAF = 0x01;
    static final int FLAG_INTERNAL = 0x02;
    static final int PAGE_HEADER_BYTES = 8; // int numEntries/numKeys + int flags

    private final Path filePath;
    private final int pageSizeBytes;
    private final int entrySize;

    // Derived capacities
    private final int leafCapacity;
    private final int internalCapacity;

    public PagedBTreeWriter(Path filePath, int pageSizeBytes, int entrySize) {
        if (pageSizeBytes < 512) {
            throw new IllegalArgumentException("Page size must be at least 512 bytes");
        }
        if (Integer.bitCount(pageSizeBytes) != 1) {
            throw new IllegalArgumentException("Page size must be a power of 2");
        }
        if (entrySize < 1 || entrySize > 8) {
            throw new IllegalArgumentException("Entry size must be between 1 and 8");
        }

        this.filePath = filePath;
        this.pageSizeBytes = pageSizeBytes;
        this.entrySize = entrySize;

        // Max entries per leaf = (pageSize - header) / (entrySize * 8)
        this.leafCapacity = (pageSizeBytes - PAGE_HEADER_BYTES) / (entrySize * 8);
        // Max keys per internal = floor((pageSize - header - 8) / 16)
        // because: numKeys keys (8 bytes each) + numKeys+1 children (8 bytes each) + header
        // header + numKeys*8 + (numKeys+1)*8 <= pageSize
        // numKeys*16 + 8 + header <= pageSize
        // numKeys <= (pageSize - header - 8) / 16
        this.internalCapacity = (pageSizeBytes - PAGE_HEADER_BYTES - 8) / 16;

        if (leafCapacity < 2) {
            throw new IllegalArgumentException("Page size too small for entry size");
        }
        if (internalCapacity < 2) {
            throw new IllegalArgumentException("Page size too small for internal nodes");
        }
    }

    @Override
    public long calculateSize(int numEntries) {
        if (numEntries <= 0) {
            return pageSizeBytes; // just the header page
        }

        // Number of leaf pages
        int numLeaves = ceilDiv(numEntries, leafCapacity);

        // Calculate internal pages level by level
        long totalPages = 1; // header
        totalPages += numLeaves;

        int numChildren = numLeaves;
        while (numChildren > 1) {
            int numParents = ceilDiv(numChildren, internalCapacity + 1);
            totalPages += numParents;
            numChildren = numParents;
        }

        return totalPages * pageSizeBytes;
    }

    @Override
    public void write(int numEntries, BTreeDataSink callback) throws IOException {
        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING))
        {
            if (numEntries == 0) {
                writeHeader(channel, 0, 0, 0);
                return;
            }

            // Collect all entries via the callback
            long[] entries = new long[numEntries * entrySize];
            CollectingEntryWriter collector = new CollectingEntryWriter(entries, entrySize);
            callback.write(collector);

            if (collector.count != numEntries) {
                throw new IllegalStateException(
                        "Expected " + numEntries + " entries but got " + collector.count);
            }

            // Verify entries are sorted
            for (int i = 1; i < numEntries; i++) {
                long prev = entries[(i - 1) * entrySize];
                long curr = entries[i * entrySize];
                if (prev >= curr) {
                    throw new IllegalArgumentException(
                            "Entries must be in strictly ascending key order; " +
                            "key[" + (i-1) + "]=" + prev + " >= key[" + i + "]=" + curr);
                }
            }

            // Write placeholder header
            writeHeader(channel, 0, 0, 0);

            // Write leaf pages
            int numLeaves = ceilDiv(numEntries, leafCapacity);
            long[] leafOffsets = new long[numLeaves];

            ByteBuffer pageBuf = ByteBuffer.allocate(pageSizeBytes).order(java.nio.ByteOrder.nativeOrder());

            for (int leaf = 0; leaf < numLeaves; leaf++) {
                leafOffsets[leaf] = channel.position();

                int startEntry = leaf * leafCapacity;
                int endEntry = Math.min(startEntry + leafCapacity, numEntries);
                int count = endEntry - startEntry;

                pageBuf.clear();
                pageBuf.putInt(count);
                pageBuf.putInt(FLAG_LEAF);

                for (int e = startEntry; e < endEntry; e++) {
                    for (int s = 0; s < entrySize; s++) {
                        pageBuf.putLong(entries[e * entrySize + s]);
                    }
                }

                // Zero-fill remainder
                while (pageBuf.hasRemaining()) {
                    pageBuf.put((byte) 0);
                }

                pageBuf.flip();
                writeFully(channel, pageBuf);
            }

            // Build internal levels bottom-up
            long[] childOffsets = leafOffsets;
            // For separator keys: use the max key from each child except the last
            long[] childMaxKeys = new long[numLeaves];
            for (int i = 0; i < numLeaves; i++) {
                int startEntry = i * leafCapacity;
                int endEntry = Math.min(startEntry + leafCapacity, numEntries) - 1;
                childMaxKeys[i] = entries[endEntry * entrySize];
            }

            int height = 1;

            while (childOffsets.length > 1) {
                int numParents = ceilDiv(childOffsets.length, internalCapacity + 1);
                long[] parentOffsets = new long[numParents];
                long[] parentMaxKeys = new long[numParents];

                for (int p = 0; p < numParents; p++) {
                    parentOffsets[p] = channel.position();

                    int firstChild = p * (internalCapacity + 1);
                    int lastChild = Math.min(firstChild + internalCapacity + 1, childOffsets.length);
                    int numChildren = lastChild - firstChild;
                    int numKeys = numChildren - 1;

                    pageBuf.clear();
                    pageBuf.putInt(numKeys);
                    pageBuf.putInt(FLAG_INTERNAL);

                    // Write separator keys (max key of each child except the last)
                    for (int k = 0; k < numKeys; k++) {
                        pageBuf.putLong(childMaxKeys[firstChild + k]);
                    }

                    // Write child offsets
                    for (int c = firstChild; c < lastChild; c++) {
                        pageBuf.putLong(childOffsets[c]);
                    }

                    // Zero-fill remainder
                    while (pageBuf.hasRemaining()) {
                        pageBuf.put((byte) 0);
                    }

                    pageBuf.flip();
                    writeFully(channel, pageBuf);

                    parentMaxKeys[p] = childMaxKeys[lastChild - 1];
                }

                childOffsets = parentOffsets;
                childMaxKeys = parentMaxKeys;
                height++;
            }

            // Rewrite header with correct root offset and height
            long rootOffset = childOffsets[0];
            writeHeader(channel, numEntries, rootOffset, height);
        }
    }

    private void writeHeader(FileChannel channel, int numEntries, long rootOffset, int height) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(pageSizeBytes).order(java.nio.ByteOrder.nativeOrder());
        header.putInt(MAGIC);
        header.putInt(FORMAT_VERSION);
        header.putInt(pageSizeBytes);
        header.putInt(entrySize);
        header.putInt(numEntries);
        header.putLong(rootOffset);
        header.putInt(height);

        while (header.hasRemaining()) {
            header.put((byte) 0);
        }

        header.flip();
        channel.position(0);
        writeFully(channel, header);
    }

    private static void writeFully(FileChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static class CollectingEntryWriter implements EntryWriter {
        private final long[] entries;
        private final int entrySize;
        int count = 0;

        CollectingEntryWriter(long[] entries, int entrySize) {
            this.entries = entries;
            this.entrySize = entrySize;
        }

        @Override
        public void put(long key, long value) {
            int base = count * entrySize;
            entries[base] = key;
            if (entrySize > 1) {
                entries[base + 1] = value;
            }
            count++;
        }
    }
}
