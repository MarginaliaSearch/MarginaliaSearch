package nu.marginalia.wmsa.client.exception;

public class TimeoutException extends MessagingException {
    public TimeoutException() {
    }
    public TimeoutException(String message) {
        super(message);
    }
    public TimeoutException(Throwable cause) {
        super(cause);
    }
    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
