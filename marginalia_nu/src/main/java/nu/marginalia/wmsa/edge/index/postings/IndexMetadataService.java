package nu.marginalia.wmsa.edge.index.postings;

public class IndexMetadataService {
    private final SearchIndexControl indexes;

    public IndexMetadataService(SearchIndexControl indexes) {
        this.indexes = indexes;
    }

    public long getDocumentMetadata(long urlId) {
        return indexes.getIndex().getDocumentMetadata(urlId);
    }

    public int getDomainId(long urlId) {
        return indexes.getIndex().getDomainId(urlId);
    }

    public long[] getTermMetadata(int termId, long[] docIdsAll) {
        return indexes.getIndex().getTermMetadata(termId, docIdsAll);
    }

}
