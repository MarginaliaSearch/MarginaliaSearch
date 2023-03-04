package nu.marginalia.client.exception;

public class RemoteException extends MessagingException {
    public RemoteException() {
    }
    public RemoteException(String message) {
        super(message);
    }
    public RemoteException(Throwable cause) {
        super(cause);
    }
    public RemoteException(String message, Throwable cause) {
        super(message, cause);
    }

}
