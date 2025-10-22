package nu.marginalia.index.searchset;

import it.unimi.dsi.fastutil.ints.IntList;

public class SearchSetAny implements SearchSet {
    @Override
    public boolean contains(int domainId) {
        return true;
    }

    @Override
    public IntList domainIds() {
        return IntList.of();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean imposesConstraint() {
        return false;
    }
}
