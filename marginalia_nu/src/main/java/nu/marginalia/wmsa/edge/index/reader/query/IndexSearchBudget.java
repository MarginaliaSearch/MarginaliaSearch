package nu.marginalia.wmsa.edge.index.reader.query;


public class IndexSearchBudget {
    private long timeout;

    public IndexSearchBudget(long limitTime) {
        this.timeout = System.currentTimeMillis() + limitTime;
    }

    // Used for short-circuiting Stream-objects using takeWhile, we don't care
    public boolean take(long unused) {
        return hasTimeLeft();
    }
    public boolean hasTimeLeft() { return System.currentTimeMillis() < timeout; }

}
