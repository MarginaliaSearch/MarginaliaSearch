package nu.marginalia.wmsa.client.exception;

public class MessagingException extends RuntimeException {
    public MessagingException() {
    }
    public MessagingException(String message) {
        super(message);
    }
    public MessagingException(Throwable cause) {
        super(cause);
    }
    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
