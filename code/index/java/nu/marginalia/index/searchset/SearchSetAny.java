package nu.marginalia.index.searchset;

public class SearchSetAny implements SearchSet {
    @Override
    public boolean contains(int domainId) {
        return true;
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
