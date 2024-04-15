package nu.marginalia.index.results.model;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.DocMetadataList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermMetadataForCombinedDocumentIds {
    private static final Logger logger = LoggerFactory.getLogger(TermMetadataForCombinedDocumentIds.class);
    private final Long2ObjectArrayMap<DocumentsWithMetadata> termdocToMeta;

    public TermMetadataForCombinedDocumentIds(Long2ObjectArrayMap<DocumentsWithMetadata> termdocToMeta) {
        this.termdocToMeta = termdocToMeta;
    }

    public long getTermMetadata(long termId, long combinedId) {
        var metaByCombinedId = termdocToMeta.get(termId);
        if (metaByCombinedId == null) {
            return 0;
        }
        return metaByCombinedId.get(combinedId);
    }

    public boolean hasTermMeta(long termId, long combinedId) {
        var metaByCombinedId = termdocToMeta.get(termId);

        if (metaByCombinedId == null) {
            return false;
        }

        return metaByCombinedId.get(combinedId) != 0;
    }

    public record DocumentsWithMetadata(Long2LongOpenHashMap data) {
        public DocumentsWithMetadata(CombinedDocIdList combinedDocIdsAll, DocMetadataList metadata) {
            this(new Long2LongOpenHashMap(combinedDocIdsAll.array(), metadata.array()));
        }

        public long get(long combinedId) {
            return data.getOrDefault(combinedId, 0);
        }
    }
}
