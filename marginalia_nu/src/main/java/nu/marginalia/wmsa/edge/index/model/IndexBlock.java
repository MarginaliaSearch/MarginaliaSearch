package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    Title(IndexBlockType.PAGE_DATA),
    Meta(IndexBlockType.PAGE_DATA),

    Words_1(IndexBlockType.PAGE_DATA),
    Words_2(IndexBlockType.PAGE_DATA),
    Words_4(IndexBlockType.PAGE_DATA),
    Words_8(IndexBlockType.PAGE_DATA),
    Words_16Plus(IndexBlockType.PAGE_DATA),

    Link(IndexBlockType.QUALITY_SIGNAL),
    Site(IndexBlockType.QUALITY_SIGNAL),

    Artifacts(IndexBlockType.PAGE_DATA),

    Tfidf_High(IndexBlockType.TRANSIENT),
    Subjects(IndexBlockType.TRANSIENT)
    ;

    public final IndexBlockType type;

    IndexBlock(IndexBlockType type) {
        this.type = type;
    }

    // This is kind of a hot method, and Enum.values() allocates a new
    // array each call.
    private static final IndexBlock[] values = IndexBlock.values();
    public static IndexBlock byId(int id) {
        return values[id];
    }
}


