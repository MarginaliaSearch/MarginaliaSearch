package nu.marginalia.index.query;


/** An execution time budget for index search operations. */
public class IndexSearchBudget {
    private final long timeout;
    private final long limitTime;

    public IndexSearchBudget(long limitTime) {
        this.timeout = System.currentTimeMillis() + limitTime;
        this.limitTime = limitTime;
    }

    public boolean hasTimeLeft() { return System.currentTimeMillis() < timeout; }
    public long timeLeft() { return Math.max(0, timeout - System.currentTimeMillis()); }
}
