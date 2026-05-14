package nu.marginalia.browse;

/**
 * Logical sets within EC_RANDOM_DOMAINS, identified by the integer values stored in
 * DOMAIN_SET. The numeric id is the in-db identity.
 */
public enum RandomDomainSet {
    DATING(0),
    EXPLORE(1),
    APPROVED_SUGGESTIONS(2);

    public final int setId;

    RandomDomainSet(int setId) {
        this.setId = setId;
    }
}
