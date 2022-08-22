package nu.marginalia.wmsa.edge.crawling.retreival;

public class RateLimitException extends Exception {
    private final String retryAfter;

    public RateLimitException(String retryAfter) {
        this.retryAfter = retryAfter;
    }

    @Override
    public StackTraceElement[] getStackTrace() { return new StackTraceElement[0]; }

    public int retryAfter() {
        try {
            return Integer.parseInt(retryAfter);
        }
        catch (NumberFormatException ex) {
            return 1000;
        }
    }
}
