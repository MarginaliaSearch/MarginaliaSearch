package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFileWriter;
import nu.marginalia.array.LongArrayWriter;

import java.io.IOException;
import java.nio.file.Path;

import static nu.marginalia.array.algo.TwoArrayOperations.mergeArraysN;

final class FullPreindexDocWriter implements LongArrayWriter, AutoCloseable {
    private final LongArrayFileWriter raw;
    private final CompressedFullPreindexDocuments.Writer compressed;

    FullPreindexDocWriter(Path path, boolean compress) throws IOException {
        if (compress) {
            raw = null;
            compressed = new CompressedFullPreindexDocuments.Writer(path);
        }
        else {
            raw = LongArrayFileWriter.create(path);
            compressed = null;
        }
    }

    @Override
    public void put(LongArray source, long start, long end) throws IOException {
        if (compressed != null) {
            compressed.put(source, start, end);
        }
        else {
            raw.put(source, start, end);
        }
    }

    long merge(LongArray left, LongArray right, long a, long aEnd, long b, long bEnd) throws IOException {
        return mergeArraysN(3, this, left, right, a, aEnd, b, bEnd);
    }

    @Override
    public void close() throws IOException {
        if (compressed != null) {
            compressed.close();
        }
        else {
            raw.close();
        }
    }
}
