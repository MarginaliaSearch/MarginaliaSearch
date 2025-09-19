package nu.marginalia.index.reverse.construction;

public interface DocIdRewriter {
    long rewriteDocId(long docId);

    static DocIdRewriter identity() {
        return l -> l;
    }
}
