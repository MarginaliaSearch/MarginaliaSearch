package nu.marginalia.index.searchset;

public interface SearchSet {

    /**
     *  Returns true if the given urlId is contained in the set
     *  or if the documentMetadata vibes with the set
     *
     */
    boolean contains(int urlId, long documentMetadata);

}
