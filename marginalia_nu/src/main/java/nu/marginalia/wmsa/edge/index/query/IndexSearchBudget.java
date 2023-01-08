package nu.marginalia.wmsa.edge.index.query;


public class IndexSearchBudget {
    private final long timeout;

    public IndexSearchBudget(long limitTime) {
        this.timeout = System.currentTimeMillis() + limitTime;
    }

    public boolean hasTimeLeft() { return System.currentTimeMillis() < timeout; }
}
