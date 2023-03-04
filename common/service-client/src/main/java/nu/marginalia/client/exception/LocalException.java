package nu.marginalia.client.exception;

public class LocalException extends MessagingException {
    public LocalException() {
    }
    public LocalException(String message) {
        super(message);
    }
    public LocalException(Throwable cause) {
        super(cause);
    }
    public LocalException(String message, Throwable cause) {
        super(message, cause);
    }
}
