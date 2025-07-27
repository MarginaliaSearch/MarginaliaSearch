package nu.marginalia.index.query;


/** An execution time budget for index search operations. */
public class IndexSearchBudget {
    private final long timeout;

    public IndexSearchBudget(long limitTime) {
        this.timeout = System.currentTimeMillis() + limitTime;
    }

    public boolean hasTimeLeft() { return System.currentTimeMillis() < timeout; }
    public long timeLeft() { return Math.max(0, timeout - System.currentTimeMillis()); }
}
