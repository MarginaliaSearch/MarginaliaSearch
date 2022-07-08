package nu.marginalia.gemini.plugins;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.gemini.GeminiService;
import nu.marginalia.gemini.io.GeminiConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class BareStaticPagePlugin implements Plugin {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Path geminiServerRoot;

    @Inject
    public BareStaticPagePlugin(@Named("gemini-server-root") Path geminiServerRoot) {
        this.geminiServerRoot = geminiServerRoot;
    }

    @Override
    public boolean serve(URI url, GeminiConnection connection) throws IOException {

        final Path serverPath = getServerPath(url.getPath());

        if (!Files.isRegularFile(serverPath)) {
            return false;
        }

        verifyPath(geminiServerRoot, serverPath);
        logger.info("Serving {}", serverPath);

        connection.respondWithFile(serverPath, FileType.match(serverPath));

        return true;
    }

    private Path getServerPath(String requestPath) {
        final Path serverPath = Path.of(geminiServerRoot + requestPath);

        if (Files.isDirectory(serverPath) && Files.isRegularFile(serverPath.resolve(GeminiService.DEFAULT_FILENAME))) {
            return serverPath.resolve(GeminiService.DEFAULT_FILENAME);
        }

        return serverPath;
    }

}
