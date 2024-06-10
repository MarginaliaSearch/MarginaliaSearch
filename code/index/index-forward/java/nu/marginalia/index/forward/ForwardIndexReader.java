package nu.marginalia.index.forward;

import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.index.forward.ForwardIndexParameters.*;

/** Reads the forward index.
 * <p/>
 * The forward index is constructed of a staggered array
 * called 'data' containing domains and document level metadata,
 * and a mapping between document identifiers to the index into the
 * data array.
 * <p/>
 * Since the total data is relatively small, this is kept in memory to
 * reduce the amount of disk thrashing.
 * <p/>
 * The metadata is a binary encoding of {@see nu.marginalia.idx.DocumentMetadata}
 */
public class ForwardIndexReader {
    private final TLongIntHashMap idToOffset;
    private final LongArray data;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ForwardIndexReader(Path idsFile, Path dataFile) throws IOException {
        if (!Files.exists(dataFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", dataFile);
            idToOffset = null;
            data = null;
            return;
        }
        else if (!Files.exists(idsFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", idsFile);
            idToOffset = null;
            data = null;
            return;
        }

        logger.info("Switching forward index");

        idToOffset = loadIds(idsFile);
        data = loadData(dataFile);
    }

    private static TLongIntHashMap loadIds(Path idsFile) throws IOException {
        try (var idsArray = LongArrayFactory.mmapForReadingShared(idsFile)) {
            assert idsArray.size() < Integer.MAX_VALUE;

            var ids = new TLongIntHashMap((int) idsArray.size(), 0.5f, -1, -1);
            // This hash table should be of the same size as the number of documents, so typically less than 1 Gb
            idsArray.forEach(0, idsArray.size(), (pos, val) -> ids.put(val, (int) pos));

            return ids;
        }
    }

    private static LongArray loadData(Path dataFile) throws IOException {
        return LongArrayFactory.mmapForReadingShared(dataFile);
    }

    public long getDocMeta(long docId) {
        assert UrlIdCodec.getRank(docId) == 0 : "Forward Index Reader fed dirty reverse index id";

        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return data.get(ENTRY_SIZE * offset + METADATA_OFFSET);
    }

    public int getHtmlFeatures(long docId) {
        assert UrlIdCodec.getRank(docId) == 0 : "Forward Index Reader fed dirty reverse index id";

        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return (int) (data.get(ENTRY_SIZE * offset + FEATURES_OFFSET) & 0xFFFF_FFFFL);
    }

    public int getDocumentSize(long docId) {
        assert UrlIdCodec.getRank(docId) == 0 : "Forward Index Reader fed dirty reverse index id";

        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return (int) (data.get(ENTRY_SIZE * offset + FEATURES_OFFSET) >>> 32L);
    }


    private int idxForDoc(long docId) {
        assert UrlIdCodec.getRank(docId) == 0 : "Forward Index Reader fed dirty reverse index id";

        if (getClass().desiredAssertionStatus()) {
            long offset = idToOffset.get(docId);
            if (offset < 0) { // Ideally we'd always check this, but this is a very hot method
                logger.warn("Could not find offset for doc {}", docId);
            }
        }

        return idToOffset.get(docId);
    }


    public int totalDocCount() {
        return idToOffset.size();
    }

    public void close() {
        if (data != null)
            data.close();
    }

    public boolean isLoaded() {
        return data != null;
    }
}
