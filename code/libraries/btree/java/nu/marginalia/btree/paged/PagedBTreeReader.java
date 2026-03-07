package nu.marginalia.btree.paged;

import nu.marginalia.btree.BTreeReaderIf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.btree.paged.PagedBTreeWriter.*;

/** Reads a B+-tree from a file.
 * <p>
 * The reader traverses from the root page down to the appropriate leaf
 * for each lookup. Internal nodes are navigated via binary search on
 * separator keys. Leaf nodes are searched via binary search on entry keys.
 * <p>
 * Two page source strategies are available:
 * <ul>
 *   <li>O_DIRECT with user-space LRU caching -- use {@link #direct}</li>
 *   <li>Buffered reads via OS page cache -- use {@link #buffered}</li>
 * </ul>
 */
public class PagedBTreeReader implements BTreeReaderIf, AutoCloseable {

    private final BTreePageSource pageSource;
    private final int pageSizeBytes;
    private final int entrySize;
    private final int numEntries;
    private final long rootOffset;
    private final int height;
    private final int leafCapacity;
    private final int internalCapacity;

    /** Open a paged B+-tree file for reading using O_DIRECT with a user-space LRU buffer pool.
     *
     * @param filePath path to the B+-tree file
     * @param poolSizePages number of pages to cache in the buffer pool
     */
    public static PagedBTreeReader direct(Path filePath, int poolSizePages) throws IOException {
        HeaderData hd = readHeader(filePath);
        DirectPageSource source = new DirectPageSource(filePath, hd.pageSizeBytes, poolSizePages);
        return new PagedBTreeReader(source, hd);
    }

    /** Open a paged B+-tree file for buffered reading via pread() with fadvise(RANDOM).
     * Pages are read through the OS page cache rather than O_DIRECT.
     */
    public static PagedBTreeReader buffered(Path filePath) throws IOException {
        HeaderData hd = readHeader(filePath);
        BufferedPageSource source = new BufferedPageSource(filePath, hd.pageSizeBytes);
        return new PagedBTreeReader(source, hd);
    }

    private PagedBTreeReader(BTreePageSource pageSource, HeaderData hd) {
        this.pageSource = pageSource;
        this.pageSizeBytes = hd.pageSizeBytes;
        this.entrySize = hd.entrySize;
        this.numEntries = hd.numEntries;
        this.rootOffset = hd.rootOffset;
        this.height = hd.height;
        this.leafCapacity = (pageSizeBytes - PAGE_HEADER_BYTES) / (entrySize * 8);
        this.internalCapacity = (pageSizeBytes - PAGE_HEADER_BYTES - 8) / 16;
    }

    /** Open a paged B+-tree using an already-constructed page source.
     * This constructor is primarily for testing.
     */
    PagedBTreeReader(BTreePageSource pageSource, int pageSizeBytes, int entrySize,
                     int numEntries, long rootOffset, int height)
    {
        this.pageSource = pageSource;
        this.pageSizeBytes = pageSizeBytes;
        this.entrySize = entrySize;
        this.numEntries = numEntries;
        this.rootOffset = rootOffset;
        this.height = height;
        this.leafCapacity = (pageSizeBytes - PAGE_HEADER_BYTES) / (entrySize * 8);
        this.internalCapacity = (pageSizeBytes - PAGE_HEADER_BYTES - 8) / 16;
    }

    @Override
    public long findEntry(long key) {
        if (numEntries == 0 || height == 0) {
            return -1;
        }

        long pageOffset = rootOffset;

        // Traverse internal nodes
        for (int level = height; level > 1; level--) {
            try (BTreePage page = pageSource.get(pageOffset)) {
                int numKeys = page.getInt(0);
                // Binary search for the child to descend into
                int childIdx = internalBinarySearch(page, numKeys, key);
                // Child offsets start after keys
                int childBase = PAGE_HEADER_BYTES + numKeys * 8;
                pageOffset = page.getLong(childBase + childIdx * 8);
            }
        }

        // Search in the leaf page
        try (BTreePage page = pageSource.get(pageOffset)) {
            int count = page.getInt(0);
            int idx = leafBinarySearch(page, count, key);
            if (idx < 0) {
                return -1;
            }
            return idx;
        }
    }

    @Override
    public int numEntries() {
        return numEntries;
    }

    @Override
    public int entrySize() {
        return entrySize;
    }

    @Override
    public long getEntryValue(long entryOffset) {
        if (entrySize == 1) {
            return getEntryKey(entryOffset);
        }

        // Find which leaf page contains this entry
        int leafIndex = (int) (entryOffset / leafCapacity);
        int indexInLeaf = (int) (entryOffset % leafCapacity);

        long leafPageOffset = (long) (1 + leafIndex) * pageSizeBytes;

        try (BTreePage page = pageSource.get(leafPageOffset)) {
            int byteOffset = PAGE_HEADER_BYTES + indexInLeaf * entrySize * 8 + 8;
            return page.getLong(byteOffset);
        }
    }

    private long getEntryKey(long entryOffset) {
        int leafIndex = (int) (entryOffset / leafCapacity);
        int indexInLeaf = (int) (entryOffset % leafCapacity);

        long leafPageOffset = (long) (1 + leafIndex) * pageSizeBytes;

        try (BTreePage page = pageSource.get(leafPageOffset)) {
            int byteOffset = PAGE_HEADER_BYTES + indexInLeaf * entrySize * 8;
            return page.getLong(byteOffset);
        }
    }

    @Override
    public long[] queryData(long[] keys, int offset) {
        long[] result = new long[keys.length];

        for (int i = 0; i < keys.length; i++) {
            long idx = findEntry(keys[i]);
            if (idx >= 0) {
                result[i] = getEntryComponent(idx, offset);
            }
        }

        return result;
    }

    private long getEntryComponent(long entryOffset, int component) {
        int leafIndex = (int) (entryOffset / leafCapacity);
        int indexInLeaf = (int) (entryOffset % leafCapacity);

        long leafPageOffset = (long) (1 + leafIndex) * pageSizeBytes;

        try (BTreePage page = pageSource.get(leafPageOffset)) {
            int byteOffset = PAGE_HEADER_BYTES + indexInLeaf * entrySize * 8 + component * 8;
            return page.getLong(byteOffset);
        }
    }

    /** Binary search in an internal node for the child index to descend to.
     * Separator keys are: key[i] = max key in child[i].
     * We want the smallest i such that key >= separatorKey[i],
     * or numKeys if key is larger than all separators.
     */
    private int internalBinarySearch(BTreePage page, int numKeys, long key) {
        int low = 0;
        int high = numKeys;

        while (low < high) {
            int mid = (low + high) >>> 1;
            long sepKey = page.getLong(PAGE_HEADER_BYTES + mid * 8);
            if (key <= sepKey) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }

        return low;
    }

    /** Binary search in a leaf page for an exact key match.
     * Returns the index within the leaf (0-based from leaf start),
     * converted to a global entry offset. Returns -1 if not found.
     */
    private int leafBinarySearch(BTreePage page, int count, long key) {
        int low = 0;
        int high = count - 1;
        int entryBytes = entrySize * 8;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midKey = page.getLong(PAGE_HEADER_BYTES + mid * entryBytes);
            if (midKey < key) {
                low = mid + 1;
            } else if (midKey > key) {
                high = mid - 1;
            } else {
                return leafLocalToGlobal(page, mid);
            }
        }

        return -1;
    }

    /** Convert a leaf-local index to a global entry offset. */
    private int leafLocalToGlobal(BTreePage page, int localIndex) {
        long pageAddr = page.pageAddress();
        int leafIndex = (int) (pageAddr / pageSizeBytes) - 1; // -1 for the header page
        return leafIndex * leafCapacity + localIndex;
    }

    @Override
    public void close() {
        pageSource.close();
    }

    private static HeaderData readHeader(Path filePath) throws IOException {
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(32).order(java.nio.ByteOrder.nativeOrder());
            ch.read(header, 0);
            header.flip();

            int magic = header.getInt();
            if (magic != MAGIC) {
                throw new IOException("Not a valid paged B+-tree file (bad magic)");
            }
            return new HeaderData(
                    header.getInt(),  // pageSizeBytes
                    header.getInt(),  // entrySize
                    header.getInt(),  // numEntries
                    header.getLong(), // rootOffset
                    header.getInt()  // height
            );
        }
    }

    private record HeaderData(int pageSizeBytes, int entrySize, int numEntries,
                              long rootOffset, int height) {}
}
