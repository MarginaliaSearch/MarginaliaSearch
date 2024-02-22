package nu.marginalia.index.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IndexJournalFileNames {
    public static Path allocateName(Path base, int idx) {
        return base.resolve(String.format("page-index-%04d.dat", idx));
    }

    public static List<Path> findJournalFiles(Path baseDirectory) throws IOException {
        List<Path> ret = new ArrayList<>();

        try (var listStream = Files.list(baseDirectory)) {
            listStream
                    .filter(IndexJournalFileNames::isJournalFile)
                    .sorted()
                    .forEach(ret::add);
        }

        return ret;
    }

    public static boolean isJournalFile(Path file) {
        return file.toFile().getName().matches("page-index-\\d{4}.dat");
    }
}
