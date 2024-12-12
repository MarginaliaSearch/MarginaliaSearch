package nu.marginalia.index.query;


/** An execution time budget for index search operations. */
public class IndexSearchBudget {
    private final long timeout;
    private final long startTime;

    public IndexSearchBudget(long limitTime) {
        this.startTime = System.nanoTime();
        this.timeout = Math.min(limitTime, 10_000) * 1_000_000L;
    }

    public boolean hasTimeLeft() {
        return System.nanoTime() - startTime < timeout;
    }
    public long timeLeft() {
        return 1_000_000 * (timeout - (System.nanoTime() - startTime));
    }

}
