package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    TitleKeywords(0, 0),
    Title(1, 0),

    Link(2, 1.15),

    Subjects(3, 1.0),
    NamesWords(4, 3.0),
    Artifacts(5, 10),
    Meta(6, 7),

    Tfidf_Top(7, 1.5),
    Tfidf_Middle(8, 2),
    Tfidf_Lower(9, 3.5),

    Words_1(10, 2.0),
    Words_2(11, 3.5),
    Words_4(12, 4.0),
    Words_8(13, 4.5),
    Words_16Plus(14, 7.0),

    Site(15, 1.2),
    ;

    public final int id;
    public final double sortOrder;

    IndexBlock(int id, double sortOrder) {
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
