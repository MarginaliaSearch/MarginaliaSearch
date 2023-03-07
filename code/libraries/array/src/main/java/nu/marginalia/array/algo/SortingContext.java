package nu.marginalia.array.algo;

import java.nio.file.Path;

public record SortingContext(Path tempDir, int memorySortLimit) {
}
