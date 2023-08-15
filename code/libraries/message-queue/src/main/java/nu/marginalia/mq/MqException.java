package nu.marginalia.mq;

public class MqException extends Exception {
    public MqException(String message) {
        super(message);
    }

    public MqException(String message, Throwable cause) {
        super(message, cause);
    }
}
