package nu.marginalia.index;

import java.nio.file.Path;

public class ReverseIndexPrioFileNames {
    public static Path resolve(Path basePath, FileIdentifier identifier, FileVersion version) {
        return switch (identifier) {
            case WORDS -> switch (version) {
                case NEXT -> basePath.resolve("rev-prio-words.dat.next");
                case CURRENT -> basePath.resolve("rev-prio-words.dat");
            };
            case DOCS -> switch (version) {
                case NEXT -> basePath.resolve("rev-prio-docs.dat.next");
                case CURRENT -> basePath.resolve("rev-prio-docs.dat");
            };
        };
    }

    public enum FileVersion {
        CURRENT,
        NEXT
    }

    public enum FileIdentifier {
        WORDS,
        DOCS
    }
}
