package nu.marginalia.index.svc.searchset;

import nu.marginalia.index.searchset.SearchSet;

public class SearchSetAny implements SearchSet {
    @Override
    public boolean contains(int urlId, long meta) {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
