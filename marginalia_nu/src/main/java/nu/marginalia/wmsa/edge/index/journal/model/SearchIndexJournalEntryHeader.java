package nu.marginalia.wmsa.edge.index.journal.model;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

public record SearchIndexJournalEntryHeader(int entrySize, long documentId, IndexBlock block) {

    public static final int HEADER_SIZE_LONGS = 2;

    public SearchIndexJournalEntryHeader( EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, IndexBlock block) {
        this(-1, (long) domainId.id() << 32 | urlId.id(), block);
    }

}
