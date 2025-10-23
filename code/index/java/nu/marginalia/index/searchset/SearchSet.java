package nu.marginalia.index.searchset;

import it.unimi.dsi.fastutil.ints.IntList;

public interface SearchSet {

    /**
     *  Returns true if the given domainId is contained in the set
     *  or if the documentMetadata vibes with the set
     *
     */
    boolean contains(int domainId);

    public IntList domainIds();


    default boolean imposesConstraint() {
        return true;
    }

}
