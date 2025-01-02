package nu.marginalia.search.exceptions;

public class RedirectException extends RuntimeException {
    public final String newUrl;

    public RedirectException(String newUrl) {
        this.newUrl = newUrl;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[0];
    }
}
