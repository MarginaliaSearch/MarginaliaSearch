package nu.marginalia.renderer;

import java.io.IOException;

public class RenderingException extends IOException {
    public RenderingException(String message) {
        super(message);
    }
    public RenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
