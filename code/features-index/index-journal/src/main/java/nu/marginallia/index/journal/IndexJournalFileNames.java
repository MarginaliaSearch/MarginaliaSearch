package nu.marginallia.index.journal;

import java.nio.file.Path;

public class IndexJournalFileNames {
    public static Path resolve(Path base) {
        return base.resolve("page-index.dat");
    }
}
