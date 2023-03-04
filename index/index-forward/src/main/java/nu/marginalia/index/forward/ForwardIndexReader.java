package nu.marginalia.index.forward;

import com.upserve.uppend.blobs.NativeIO;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.array.LongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForwardIndexReader {
    private final TLongIntHashMap ids;
    private final LongArray data;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ForwardIndexReader(Path idsFile, Path dataFile) throws IOException {
        if (!Files.exists(dataFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", dataFile);
            ids = null;
            data = null;
            return;
        }
        else if (!Files.exists(idsFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", idsFile);
            ids = null;
            data = null;
            return;
        }

        logger.info("Switching forward index");

        ids = loadIds(idsFile);
        data = loadData(dataFile);
    }

    private static TLongIntHashMap loadIds(Path idsFile) throws IOException {
        var idsArray = LongArray.mmapRead(idsFile);
        idsArray.advice(NativeIO.Advice.Sequential);

        var ids = new TLongIntHashMap((int) idsArray.size(), 0.5f, -1, -1);

        // This hash table should be of the same size as the number of documents, so typically less than 1 Gb
        idsArray.forEach(0, idsArray.size(), (pos, val) -> {
            ids.put(val, (int) pos);
        });

        return ids;
    }

    private static LongArray loadData(Path dataFile) throws IOException {
        var data = LongArray.mmapRead(dataFile);

        data.advice(NativeIO.Advice.Random);

        return data;
    }

    private int idxForDoc(long docId) {
        return ids.get(docId);
    }

    public long getDocMeta(long docId) {
        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return data.get(ForwardIndexParameters.ENTRY_SIZE * offset + ForwardIndexParameters.METADATA_OFFSET);
    }

    public int getDomainId(long docId) {
        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return Math.max(0, (int) data.get(ForwardIndexParameters.ENTRY_SIZE * offset + ForwardIndexParameters.DOMAIN_OFFSET));
    }

    public DocPost docPost(long docId) {
        return new DocPost(idxForDoc(docId));
    }


    public class DocPost {
        private final long idx;

        public DocPost(int idx) {
            this.idx = idx;
        }

        public long meta() {

            if (idx < 0)
                return 0;

            return data.get(ForwardIndexParameters.ENTRY_SIZE * idx + ForwardIndexParameters.METADATA_OFFSET);
        }

        public int domainId() {
            if (idx < 0)
                return 0;

            return Math.max(0, (int) data.get(ForwardIndexParameters.ENTRY_SIZE * idx + ForwardIndexParameters.DOMAIN_OFFSET));
        }
    }
}
