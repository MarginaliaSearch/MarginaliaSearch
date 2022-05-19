package nu.marginalia.wmsa.edge.converting.model;

public class DisqualifiedException extends Exception {
    public final DisqualificationReason reason;

    public DisqualifiedException(DisqualificationReason reason) {
        this.reason = reason;
    }
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public enum DisqualificationReason {
        LENGTH, CONTENT_TYPE, LANGUAGE, STATUS, QUALITY
    }
}
