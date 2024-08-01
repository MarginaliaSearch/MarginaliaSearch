package nu.marginalia.model.processed;

import java.nio.file.Path;

public record SlopPageRef<T>(Path baseDir, int page) {
}
