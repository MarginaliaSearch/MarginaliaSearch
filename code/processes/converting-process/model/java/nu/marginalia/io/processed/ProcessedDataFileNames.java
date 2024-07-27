package nu.marginalia.io.processed;

import java.nio.file.Path;

public class ProcessedDataFileNames {
    public static Path documentFileName(Path base) {
        return base.resolve("document");
    }
    public static Path domainFileName(Path base) {
        return base.resolve("domains");
    }
    public static Path domainLinkFileName(Path base) {
        return base.resolve("domain-link");
    }

}
