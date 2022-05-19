package nu.marginalia.wmsa.client.exception;

public class NetworkException extends MessagingException {
    public NetworkException() {
    }
    public NetworkException(String message) {
        super(message);
    }
    public NetworkException(Throwable cause) {
        super(cause);
    }
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
