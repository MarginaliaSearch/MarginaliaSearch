package nu.marginalia.index.results;

import com.google.inject.Inject;
import nu.marginalia.index.index.SearchIndex;

public class IndexMetadataService {
    private final SearchIndex index;

    @Inject
    public IndexMetadataService(SearchIndex index) {
        this.index = index;
    }

    public long getDocumentMetadata(long urlId) {
        return index.getDocumentMetadata(urlId);
    }

    public int getDomainId(long urlId) {
        return index.getDomainId(urlId);
    }

    public long[] getTermMetadata(int termId, long[] docIdsAll) {
        return index.getTermMetadata(termId, docIdsAll);
    }

}
