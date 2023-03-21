package nu.marginalia.index.query;

public enum ReverseIndexEntrySourceBehavior {
    /** Eagerly read from this entry source */
    DO_PREFER,

    /** Do not use this entry source if entries have been fetched
     * from another entry source
     */
    DO_NOT_PREFER
}
