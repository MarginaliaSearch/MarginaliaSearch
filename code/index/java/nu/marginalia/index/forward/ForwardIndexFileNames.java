package nu.marginalia.index.forward;

import java.nio.file.Path;

public class ForwardIndexFileNames {
    public static Path resolve(Path basePath, FileIdentifier identifier, FileVersion version) {
        return switch (identifier) {
            case DOC_ID -> switch (version) {
                case NEXT -> basePath.resolve("fwd-doc-id.dat.next");
                case CURRENT -> basePath.resolve("fwd-doc-id.dat");
            };
            case DOC_DATA -> switch (version) {
                case NEXT -> basePath.resolve("fwd-doc-data.dat.next");
                case CURRENT -> basePath.resolve("fwd-doc-data.dat");
            };
            case SPANS_DATA -> switch (version) {
                case NEXT -> basePath.resolve("fwd-spans.dat.next");
                case CURRENT -> basePath.resolve("fwd-spans.dat");
            };
        };
    }

    public enum FileVersion {
        CURRENT,
        NEXT
    }

    public enum FileIdentifier {
        DOC_DATA,
        SPANS_DATA,
        DOC_ID
    }
}
