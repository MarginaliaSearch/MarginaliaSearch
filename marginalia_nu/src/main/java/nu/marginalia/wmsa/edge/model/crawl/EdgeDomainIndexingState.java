package nu.marginalia.wmsa.edge.model.crawl;

public enum EdgeDomainIndexingState {
    ACTIVE(0),
    EXHAUSTED(1),
    SPECIAL(2),
    SOCIAL_MEDIA(3),
    BLOCKED(-1),
    REDIR(-2),
    ERROR(-3),
    UNKNOWN(-100);

    public final int code;

    EdgeDomainIndexingState(int code) {
        this.code = code;
    }

    public static EdgeDomainIndexingState fromCode(int code) {
        for (var state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
