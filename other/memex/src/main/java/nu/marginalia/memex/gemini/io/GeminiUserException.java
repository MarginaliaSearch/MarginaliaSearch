package nu.marginalia.memex.gemini.io;

/** Throw to report message to user */
public class GeminiUserException extends RuntimeException {
    public GeminiUserException(String message) {
        super(message);
    }
}
