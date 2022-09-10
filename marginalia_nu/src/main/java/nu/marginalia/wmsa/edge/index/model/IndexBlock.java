package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    TitleKeywords(IndexBlockType.QUALITY_SIGNAL, 0, 0),
    Title(IndexBlockType.QUALITY_SIGNAL, 1, 1),

    Link(IndexBlockType.QUALITY_SIGNAL, 2, 1.15),

    Subjects(IndexBlockType.QUALITY_SIGNAL, 3, 1.0),
    NamesWords(IndexBlockType.QUALITY_SIGNAL, 4, 3.0),
    Artifacts(IndexBlockType.QUALITY_SIGNAL, 5, 10),

    Meta(IndexBlockType.PAGE_DATA, 6, 7),

    Tfidf_Top(IndexBlockType.TF_IDF, 7, 1.5),
    Tfidf_Middle(IndexBlockType.TF_IDF, 8, 2),
    Tfidf_Lower(IndexBlockType.TF_IDF, 9, 3.5),

    Words_1(IndexBlockType.PAGE_DATA, 10, 2.0),
    Words_2(IndexBlockType.PAGE_DATA,11, 3.5),
    Words_4(IndexBlockType.PAGE_DATA,12, 4.0),
    Words_8(IndexBlockType.PAGE_DATA,13, 4.5),
    Words_16Plus(IndexBlockType.PAGE_DATA,14, 7.0),

    Site(IndexBlockType.QUALITY_SIGNAL, 15, 1.2)
    ;

    public final IndexBlockType type;
    public final int id;
    public final double sortOrder;

    IndexBlock(IndexBlockType type, int id, double sortOrder) {
        this.type = type;
        this.sortOrder = sortOrder;
        this.id = id;
    }

    public static IndexBlock byId(int id) {
        for (IndexBlock block : values()) {
            if (id == block.id) {
                return block;
            }
        }
        throw new IllegalArgumentException("Bad block id");
    }
}


