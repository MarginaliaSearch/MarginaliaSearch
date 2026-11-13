package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArrayFileWriter;

import java.io.IOException;
import java.nio.file.Path;

final class FullPreindexDocWriter implements AutoCloseable {
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

    void put(long documentId, long positionOffset, long metadata) throws IOException {
        if (compressed != null) {
            compressed.put(documentId, positionOffset, metadata);
        }
        else {
            raw.put(documentId);
            raw.put(positionOffset);
            raw.put(metadata);
        }
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
