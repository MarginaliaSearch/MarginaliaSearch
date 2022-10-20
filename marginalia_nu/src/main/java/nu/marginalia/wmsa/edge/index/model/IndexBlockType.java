package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlockType {
    /** This block is only used for joins */
    QUALITY_SIGNAL,
    /** This block contains page keywords */
    PAGE_DATA,
    /** This block is only used for generation */
    TRANSIENT
}
