package nu.marginalia.converting.processor;

/** Depending on external factors, such as how often a document is linked,
 * quality and length rules are selectively enforced.
 */
public enum DocumentClass {
    NORMAL,
    EXTERNALLY_LINKED_ONCE,
    EXTERNALLY_LINKED_MULTI,
    /** A document that is not linked to, but is sideloaded.  Ignore most inclusion checks. */
    SIDELOAD;

    public boolean enforceQualityLimits() {
        if (this == SIDELOAD)
            return false;
        if (this == EXTERNALLY_LINKED_MULTI)
            return false;
        return true;
    }

    /** This factor is multiplied onto the length of the document
     * when determining whether it's sufficiently long to be indexed
     */
    public double lengthLimitModifier() {
        return switch (this) {
            case NORMAL -> 1.0;
            case EXTERNALLY_LINKED_ONCE -> 2.;
            case EXTERNALLY_LINKED_MULTI -> 10.;
            case SIDELOAD -> 25.;
        };
    }
}
