package nu.marginalia.converting.processor;

/** Depending on external factors, such as how often a document is linked,
 * quality and length rules are selectively enforced.
 */
public enum DocumentClass {
    NORMAL,
    EXTERNALLY_LINKED_ONCE,
    EXTERNALLY_LINKED_MULTI;

    public boolean enforceQualityLimits() {
        return this != EXTERNALLY_LINKED_MULTI;
    }

    /** This factor is multiplied onto the length of the document
     * when determining whether it's sufficiently long to be indexed
     */
    public double lengthLimitModifier() {
        return switch (this) {
            case NORMAL -> 1.0;
            case EXTERNALLY_LINKED_ONCE -> 2.;
            case EXTERNALLY_LINKED_MULTI -> 10.;
        };
    }
}
