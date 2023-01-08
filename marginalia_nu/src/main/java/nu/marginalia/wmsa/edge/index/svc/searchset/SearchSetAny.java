package nu.marginalia.wmsa.edge.index.svc.searchset;

public class SearchSetAny implements SearchSet {
    @Override
    public boolean contains(int urlId) {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
