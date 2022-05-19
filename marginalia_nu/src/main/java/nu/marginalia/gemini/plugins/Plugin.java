package nu.marginalia.gemini.plugins;

import nu.marginalia.gemini.io.GeminiConnection;
import nu.marginalia.gemini.io.GeminiUserException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public interface Plugin {
    /** @return true if content served */
    boolean serve(URI url, GeminiConnection connection) throws IOException;

    default void verifyPath(Path root, Path p) {
        if (!p.normalize().startsWith(root)) {
            throw new GeminiUserException("ಠ_ಠ    That path is off limits!");
        }
    }
}
