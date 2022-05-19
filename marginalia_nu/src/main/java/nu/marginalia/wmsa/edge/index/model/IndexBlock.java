package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    TitleKeywords(0, 0),
    Title(1, 1),
    Link(2, 1.25),
    Top(3, 2),
    Middle(4, 3),
    Low(5, 4),
    Words(6, 6),
    Meta(7, 7),
    PositionWords(8, 4.5),
    NamesWords(9, 5),
    TermFreq(10, 10),
    Topic(11, 0.5);

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
