package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.skiplist.SkipListWriter;

import java.io.IOException;
import java.nio.file.Path;

/** Constructs the skiplists in a reverse index */
public class FullIndexSkipListTransformer implements LongArrayTransformations.LongIOTransformer, AutoCloseable {
    private final SkipListWriter writer;
    private final LongArray documentsArray;

    long start = 0;

    public FullIndexSkipListTransformer(Path docsOutputFile,
                                        Path docsValuesOutputFile,
                                        LongArray documentsArray) throws IOException {
        this.documentsArray = documentsArray;
        this.writer = new SkipListWriter(docsOutputFile, docsValuesOutputFile);
    }

    @Override
    public long transform(long pos, long end) throws IOException {

        final int size = (int) ((end - start) / FullPreindexDocuments.RECORD_SIZE_LONGS);

        if (size == 0) {
            return -1;
        }

        long offset = writer.writeList(documentsArray.shifted(start), size);
        start = end;
        return offset;
    }

    public void close() throws IOException {
        writer.close();
    }
}
