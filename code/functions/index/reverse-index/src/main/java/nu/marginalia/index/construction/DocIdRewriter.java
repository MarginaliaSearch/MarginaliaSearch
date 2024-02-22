package nu.marginalia.index.construction;

public interface DocIdRewriter {
    long rewriteDocId(long docId);

    static DocIdRewriter identity() {
        return l -> l;
    }
}
