package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    TitleKeywords(0, 0),
    Title(1, 1),
    Link(2, 1.25),
    Top(3, 2),
    Middle(4, 2.5),
    Low(5, 3.0),
    Words_1(6, 3.0),
    Meta(7, 7),
    Words_2(8, 3.5),
    NamesWords(9, 5),
    Artifacts(10, 10),
    Topic(11, 0.5),
    Words_4(12, 4.0),
    Words_8(13, 4.5),
    Words_16Plus(14, 7.0),
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
