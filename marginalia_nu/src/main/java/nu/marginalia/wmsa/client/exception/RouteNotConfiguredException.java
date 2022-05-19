package nu.marginalia.wmsa.client.exception;

public class RouteNotConfiguredException extends MessagingException {
    public RouteNotConfiguredException() {
    }
    public RouteNotConfiguredException(String message) {
        super(message);
    }
    public RouteNotConfiguredException(Throwable cause) {
        super(cause);
    }
    public RouteNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
