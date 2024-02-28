package nu.marginalia.control;

public class ControlValidationError extends RuntimeException {
    public final String title;
    public final String messageLong;
    public final String redirect;

    public ControlValidationError(String title, String messageLong, String redirect) {
        super(title);

        this.title = title;
        this.messageLong = messageLong;
        this.redirect = redirect;
    }
}
