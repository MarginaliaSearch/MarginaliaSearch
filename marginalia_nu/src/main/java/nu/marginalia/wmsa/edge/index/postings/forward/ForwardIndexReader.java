package nu.marginalia.wmsa.edge.index.postings.forward;

import com.upserve.uppend.blobs.NativeIO;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.util.array.LongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.wmsa.edge.index.postings.forward.ForwardIndexParameters.*;

public class ForwardIndexReader {
    private final TLongIntHashMap ids;
    private final LongArray data;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ForwardIndexReader(Path idsFile, Path dataFile) throws IOException {
        if (!Files.exists(dataFile) ||
            !Files.exists(idsFile)
        ) {
            ids = null;
            data = null;
            return;
        }

        logger.info("Switching forward index");

        var idsArray = LongArray.mmapRead(idsFile);
        idsArray.advice(NativeIO.Advice.Sequential);

        ids = new TLongIntHashMap((int) idsArray.size(), 0.5f, -1, -1);

        // This hash table should be of the same size as the number of documents, so typically less than 1 Gb
        idsArray.forEach(0, idsArray.size(), (pos, val) -> {
            ids.put(val, (int) pos);
        });

        data = LongArray.mmapRead(dataFile);


        data.advice(NativeIO.Advice.Random);
    }

    private int idxForDoc(long docId) {
        return ids.get(docId);
    }

    public long getDocMeta(long docId) {
        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return data.get(ENTRY_SIZE * offset + METADATA_OFFSET);
    }
    public int getDomainId(long docId) {
        long offset = idxForDoc(docId);
        if (offset < 0) return 0;

        return Math.max(0, (int) data.get(ENTRY_SIZE * offset + DOMAIN_OFFSET));
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

            return data.get(ENTRY_SIZE * idx + METADATA_OFFSET);
        }

        public int domainId() {
            if (idx < 0)
                return 0;

            return Math.max(0, (int) data.get(ENTRY_SIZE * idx + DOMAIN_OFFSET));
        }
    }
}
