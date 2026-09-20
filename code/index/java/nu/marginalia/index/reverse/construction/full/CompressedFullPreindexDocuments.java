package nu.marginalia.index.reverse.construction.full;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.array.LongArray;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

/** Read-only, seekable compressed storage for full preindex postings.
 */
final class CompressedFullPreindexDocuments implements LongArray {
    static final int DEFAULT_BLOCK_RECORDS = 32 * 1024;
    private static final int FORMAT_VERSION = 1;
    private static final long MAGIC = 0x4650524549445831L;
    private static final int HEADER_BYTES = 32;
    private static final int CACHE_BLOCKS = 4;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private final Storage storage;
    private final long start;
    private final long length;
    private final boolean owner;

    static CompressedFullPreindexDocuments open(Path path) throws IOException {
        Storage storage = new Storage(path);
        return new CompressedFullPreindexDocuments(storage, 0, storage.length, true);
    }

    private CompressedFullPreindexDocuments(Storage storage, long start, long length, boolean owner) {
        this.storage = storage;
        this.start = start;
        this.length = length;
        this.owner = owner;
    }

    @Override
    public long get(long pos) {
        Objects.checkIndex(pos, length);

        long absolute = start + pos;

        long[] block = storage.blockAt(absolute);
        return block[(int) (absolute - storage.currentStart)];
    }

    /** Copy decoded longs without routing them through a contiguous LongArray backing store. */
    void copyTo(long pos, MemorySegment destination, int destinationPos, int count) {
        Objects.checkFromIndexSize(pos, count, length);

        long absolute = start + pos;

        while (count > 0) {
            long[] block = storage.blockAt(absolute);

            int within = (int) (absolute - storage.currentStart);
            int chunk = Math.min(count, storage.blockLongs - within);

            MemorySegment.copy(block, within, destination, ValueLayout.JAVA_LONG, 8L * destinationPos, chunk);

            absolute += chunk;
            destinationPos += chunk;
            count -= chunk;
        }
    }

    @Override
    public long size() { return length; }

    @Override
    public LongArray range(long from, long to) {
        Objects.checkFromToIndex(from, to, length);

        return new CompressedFullPreindexDocuments(storage, start + from, to - from, false);
    }

    @Override
    public LongArray shifted(long offset) { return range(offset, length); }

    @Override
    public void force() { /* Read-only; writers are closed before opening. */ }

    @Override
    public void close() {
        if (owner) {
            storage.close();
        }
    }

    @Override
    public boolean hasMemorySegment() { return false; }

    @Override
    public MemorySegment getMemorySegment() {
        throw new UnsupportedOperationException("Compressed postings have no contiguous memory segment");
    }

    @Override
    public void set(long pos, long value) { throw readOnly(); }

    @Override
    public void write(Path file) { throw readOnly(); }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) { throw readOnly(); }

    @Override
    public void transferFrom(LongArray source, long sourceStart, long arrayStart, long arrayEnd) { throw readOnly(); }

    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("Compressed preindex postings are read-only");
    }

    private static final class Storage implements AutoCloseable {
        private final Arena arena = Arena.ofShared();

        private ZstdDecompressCtx decoder;
        private final MemorySegment file;

        private final long length;

        private final int blockLongs;
        private final long indexOffset;

        private MemorySegment transformed;
        private ByteBuffer transformedBuffer;
        private MemorySegment decodedBlock;

        private final long[][] decoded = new long[CACHE_BLOCKS][];
        private final long[] blockIds = new long[CACHE_BLOCKS];

        private long[] current;
        private long currentStart;
        private long currentEnd;

        private boolean closed;

        Storage(Path path) throws IOException {
            Arrays.fill(blockIds, -1);

            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                if (channel.size() < HEADER_BYTES + 8)
                    throw new IOException("Truncated compressed preindex: " + path);

                file = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);

                ByteBuffer header = file.asSlice(0, HEADER_BYTES)
                                        .asByteBuffer()
                                        .order(ByteOrder.LITTLE_ENDIAN);

                if (header.getLong() != MAGIC)
                    throw new IOException("Invalid compressed preindex header: " + path);
                if (header.getInt() != FORMAT_VERSION)
                    throw new IOException("Unsupported compressed preindex version: " + path);

                blockLongs = header.getInt();
                length = header.getLong();
                indexOffset = header.getLong();

                if (blockLongs <= 0 || blockLongs % 3 != 0 || blockLongs > 3 * 1024 * 1024
                        || length < 0 || length % 3 != 0 || indexOffset < HEADER_BYTES || indexOffset > file.byteSize() - 8)
                    throw new IOException("Invalid compressed preindex dimensions: " + path);

                long blocks = Math.ceilDiv(length, blockLongs);

                if (blocks + 1 != (file.byteSize() - indexOffset) / 8 || (file.byteSize() - indexOffset) % 8 != 0
                        || offset(0) != HEADER_BYTES || offset(blocks) != indexOffset)
                    throw new IOException("Invalid compressed preindex block directory: " + path);

                decoder = new ZstdDecompressCtx();
            }
            catch (IOException | RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        private long offset(long block) { return file.get(LONG, indexOffset + 8 * block); }

        long[] blockAt(long position) {
            if (closed)
                throw new IllegalStateException("Compressed preindex is closed");

            // Merging and skiplist construction mostly walk forwards. Avoid division and
            // directory/cache lookup on every individual long in an already decoded block.
            if (current != null
                    && position >= currentStart
                    && position < currentEnd)
                return current;

            current = null;

            long block = position / blockLongs;
            long[] result = loadBlock(block);

            currentStart = block * blockLongs;
            currentEnd = Math.min(length, currentStart + blockLongs);
            current = result;

            return result;
        }

        private long[] loadBlock(long block) {
            int slot = (int) (block % CACHE_BLOCKS);

            if (blockIds[slot] == block) {
                return decoded[slot];
            }

            long from = offset(block);
            long to = offset(block + 1);

            int expectedBytes = (int) (8 * Math.min(blockLongs, length - block * blockLongs));

            if (from < HEADER_BYTES || to <= from || to > indexOffset || to - from > Zstd.compressBound(8L * blockLongs))
                throw new UncheckedIOException(new IOException("Invalid compressed preindex block " + block));

            if (decoded[slot] == null) {
                decoded[slot] = new long[blockLongs];
            }

            if (transformed == null) {
                transformed = arena.allocate(8L * blockLongs, 8);
                transformedBuffer = transformed.asByteBuffer();
                decodedBlock = arena.allocate(8L * blockLongs, 8);
            }

            // Do not retain a cache hit for a buffer whose replacement failed validation.
            blockIds[slot] = -1;
            try {
                int bytes = decoder.decompressDirectByteBuffer(transformedBuffer, 0, expectedBytes,
                        file.asSlice(from, to - from).asByteBuffer(), 0, (int) (to - from));

                if (bytes != expectedBytes)
                    throw new IOException("Incorrect decoded size for preindex block " + block);

                FullPreindexBlockTransform.decode(transformed, expectedBytes / 24, decodedBlock);

                // Heap cache keeps the individual long reads in merge/finalization cheap.
                MemorySegment.copy(decodedBlock, ValueLayout.JAVA_LONG, 0, decoded[slot], 0, expectedBytes / 8);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            catch (RuntimeException e) {
                throw new UncheckedIOException(new IOException("Corrupt compressed preindex block " + block, e));
            }
            blockIds[slot] = block;
            return decoded[slot];
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try { if (decoder != null) decoder.close(); }
            finally { arena.close(); }
        }
    }

    static final class Writer implements AutoCloseable {
        private final Arena arena;

        private final FileChannel channel;
        private final ZstdCompressCtx encoder;

        private final int blockLongs;

        private final MemorySegment bufferSegment;
        private final MemorySegment transformed;

        private final ByteBuffer transformedBuffer;
        private final ByteBuffer compressed;

        private final LongArrayList offsets = new LongArrayList();

        private int position;
        private long length;

        private boolean closed;

        Writer(Path path) throws IOException {
            this(path, Integer.getInteger("index.fullPreindexBlockRecords", DEFAULT_BLOCK_RECORDS),
                    Integer.getInteger("index.fullPreindexCompressionLevel", 1));
        }

        Writer(Path path, int blockRecords, int level) throws IOException {
            if (blockRecords <= 0 || blockRecords > 1024 * 1024)
                throw new IllegalArgumentException("Block records must be between 1 and 1048576");

            blockLongs = 3 * blockRecords;
            arena = Arena.ofConfined();

            try {
                bufferSegment = arena.allocate(8L * blockLongs, 8);
                transformed = arena.allocate(8L * blockLongs, 8);

                transformedBuffer = transformed.asByteBuffer();

                compressed = arena.allocate(Zstd.compressBound(transformed.byteSize()), 8).asByteBuffer();
                encoder = new ZstdCompressCtx();

                try {
                    encoder.setLevel(level).setChecksum(true);
                    channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                    try {
                        writeFully(channel, ByteBuffer.allocate(HEADER_BYTES));
                    }
                    catch (IOException | RuntimeException | Error e) {

                        try {
                            channel.close();
                        }
                        catch (IOException closeError) {
                            e.addSuppressed(closeError);
                        }

                        throw e;
                    }
                }
                catch (IOException | RuntimeException | Error e) {
                    encoder.close();
                    throw e;
                }
            }
            catch (IOException | RuntimeException | Error e) {
                arena.close();
                throw e;
            }
        }

        void put(LongArray source, long start, long end) throws IOException {
            if (closed) throw new IllegalStateException("Writer is closed");

            Objects.checkFromToIndex(start, end, source.size());

            while (start < end) {
                if (position == blockLongs)
                    flush();

                int chunk = (int) Math.min(end - start, blockLongs - position);

                if (source instanceof CompressedFullPreindexDocuments compressedSource) {
                    compressedSource.copyTo(start, bufferSegment, position, chunk);
                }
                else {
                    MemorySegment.copy(source.getMemorySegment(), 8 * start, bufferSegment, 8L * position, 8L * chunk);
                }

                position += chunk;
                length += chunk;
                start += chunk;
            }
        }

        private void flush() throws IOException {
            if (position == 0) return;

            FullPreindexBlockTransform.encode(bufferSegment, position / 3, transformed);

            compressed.clear();

            int bytes = encoder.compressDirectByteBuffer(compressed, 0, compressed.capacity(),
                    transformedBuffer, 0, 8 * position);

            offsets.add(channel.position());
            compressed.clear().limit(bytes);
            writeFully(channel, compressed);

            position = 0;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;

            closed = true;

            try (arena; encoder; channel) {

                if (length % 3 != 0) {
                    throw new IOException("Incomplete full preindex posting");
                }

                flush();

                long indexOffset = channel.position();
                offsets.add(indexOffset);

                ByteBuffer index = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);

                for (long offset : offsets) {
                    if (!index.hasRemaining()) {
                        index.flip();
                        writeFully(channel, index);
                        index.clear();
                    }
                    index.putLong(offset);
                }

                index.flip();

                writeFully(channel, index);

                ByteBuffer header = ByteBuffer
                        .allocate(HEADER_BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(MAGIC)
                        .putInt(FORMAT_VERSION)
                        .putInt(blockLongs)
                        .putLong(length)
                        .putLong(indexOffset)
                        .flip();

                channel.position(0);

                writeFully(channel, header);
            }
        }

        private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining())
                channel.write(buffer);
        }
    }
}
