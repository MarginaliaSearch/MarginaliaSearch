package nu.marginalia.index.results.model;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import nu.marginalia.index.reverse.positions.TermData;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermMetadataList;
import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;

public class TermMetadataForCombinedDocumentIds {
    private final Long2ObjectArrayMap<DocumentsWithMetadata> termdocToMeta;

    public TermMetadataForCombinedDocumentIds(Long2ObjectArrayMap<DocumentsWithMetadata> termdocToMeta) {
        this.termdocToMeta = termdocToMeta;
    }

    public byte getTermMetadata(long termId, long combinedId) {
        var metaByCombinedId = termdocToMeta.get(termId);
        if (metaByCombinedId == null) {
            return 0;
        }
        return metaByCombinedId.get(combinedId).flags();
    }

    @Nullable
    public CodedSequence getPositions(long termId, long combinedId) {
        var metaByCombinedId = termdocToMeta.get(termId);

        if (metaByCombinedId == null) {
            return null;
        }

        return metaByCombinedId.get(combinedId).positions();
    }

    public boolean hasTermMeta(long termId, long combinedId) {
        var metaByCombinedId = termdocToMeta.get(termId);

        if (metaByCombinedId == null) {
            return false;
        }

        return metaByCombinedId.data().containsKey(combinedId);
    }

    public record DocumentsWithMetadata(Long2ObjectOpenHashMap<TermData> data) {
        public DocumentsWithMetadata(CombinedDocIdList combinedDocIdsAll, TermMetadataList metadata) {
            this(new Long2ObjectOpenHashMap<>(combinedDocIdsAll.size()));

            long[] ids = combinedDocIdsAll.array();
            TermData[] data = metadata.array();

            for (int i = 0; i < combinedDocIdsAll.size(); i++) {
                if (data[i] != null) {
                    this.data.put(ids[i], data[i]);
                }
            }
        }

        public TermData get(long combinedId) {
            return data.get(combinedId);
        }
    }
}
