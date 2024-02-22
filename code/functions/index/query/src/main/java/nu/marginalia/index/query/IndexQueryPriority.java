package nu.marginalia.index.query;

/** Designates the presumptive value of an IndexQuery.
 */
public enum IndexQueryPriority {
    /** This is likely to produce highly relevant results */
    BEST,

    /** This may produce relevant results */
    GOOD,

    /** This is a fallback query, only execute if no higher prioritized query returned any results */
    FALLBACK
}
