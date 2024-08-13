package nu.marginalia.index.journal;

import nu.marginalia.slop.desc.SlopTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record IndexJournal(Path journalDir) {

    public static final String JOURNAL_FILE_NAME = "index-journal";

    public static Path allocateName(Path base) {
        return base.resolve(JOURNAL_FILE_NAME);
    }

    /** Returns the journal file in the base directory. */
    public static Optional<IndexJournal> findJournal(Path baseDirectory) {
        Path journal = baseDirectory.resolve(JOURNAL_FILE_NAME);
        if (Files.isDirectory(journal)) {
            return Optional.of(new IndexJournal(journal));
        }
        return Optional.empty();
    }

    /** Returns the number of versions of the journal file in the base directory. */
    public static int numPages(Path baseDirectory) {
        return SlopTable.getNumPages(baseDirectory, IndexJournalPage.combinedId);
    }

    public IndexJournal {
        if (!journalDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid journal directory: " + journalDir);
        }
    }

    public List<IndexJournalPage> pages() {
        int pages = numPages(journalDir);

        List<IndexJournalPage> instances = new ArrayList<>(pages);

        for (int version = 0; version < pages; version++) {
            instances.add(new IndexJournalPage(journalDir, version));
        }

        return instances;
    }
}
