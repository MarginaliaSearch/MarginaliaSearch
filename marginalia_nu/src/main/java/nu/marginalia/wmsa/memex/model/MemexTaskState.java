package nu.marginalia.wmsa.memex.model;

public enum MemexTaskState {
    DONE('/', true,"done"),
    SKIP('x', true,"skip"),
    SKIP2('-', true,"skip"),
    UNKNOWN('?', false, "unknown"),
    URGENT('!', false, "urgent"),
    TODO(0, false, "todo");

    public final int key;
    public final String style;
    public final boolean done;

    MemexTaskState(int key, boolean done, String style) {
        this.key = key;
        this.style = style;
        this.done = done;
    }

    public static MemexTaskState of(MemexTaskTags tags) {
        for (MemexTaskState state : values()) {
            if (tags.hasTag(state.key)) {
                return state;
            }
        }
        return TODO;
    }

}
