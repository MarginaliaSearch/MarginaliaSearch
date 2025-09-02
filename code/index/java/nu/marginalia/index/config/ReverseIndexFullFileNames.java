package nu.marginalia.index.config;

import java.nio.file.Path;

public class ReverseIndexFullFileNames {
    public static Path resolve(Path basePath, FileIdentifier identifier, FileVersion version) {
        return switch (identifier) {
            case WORDS -> switch (version) {
                case NEXT -> basePath.resolve("rev-words.dat.next");
                case CURRENT -> basePath.resolve("rev-words.dat");
            };
            case DOCS -> switch (version) {
                case NEXT -> basePath.resolve("rev-docs.dat.next");
                case CURRENT -> basePath.resolve("rev-docs.dat");
            };
            case POSITIONS -> switch (version) {
                case NEXT -> basePath.resolve("rev-positions.dat.next");
                case CURRENT -> basePath.resolve("rev-positions.dat");
            };
        };
    }

    public enum FileVersion {
        CURRENT,
        NEXT,
    }

    public enum FileIdentifier {
        WORDS,
        DOCS,
        POSITIONS,
    }
}
