package nu.marginalia.index.journal;

import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.slop.SlopTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public record IndexJournal(Path journalDir) {

    public static final String JOURNAL_FILE_NAME = "index-journal";

    public static Path allocateName(Path base, String languageIsoCode) {
        return base.resolve(JOURNAL_FILE_NAME + "-" + languageIsoCode);
    }

    /** Returns the journal file in the base directory. */
    public static Optional<IndexJournal> findJournal(Path baseDirectory, String languageIsoCode) {
        Path journal = baseDirectory.resolve(JOURNAL_FILE_NAME + "-" + languageIsoCode);
        if (Files.isDirectory(journal)) {
            return Optional.of(new IndexJournal(journal));
        }
        return Optional.empty();
    }

    public static Map<String, IndexJournal> findJournals(Path baseDirectory, Collection<LanguageDefinition> languageDefinitions) {
        Map<String, IndexJournal> ret = new HashMap<>();
        for (LanguageDefinition languageDefinition : languageDefinitions) {
            findJournal(baseDirectory, languageDefinition.isoCode()).ifPresent(ld -> ret.put(languageDefinition.isoCode(), ld));
        }
        return ret;
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

    public Set<String> languages() {
        try {
            Set<String> languages = new HashSet<>();

            for (var instance : pages()) {
                try (var slopTable = new SlopTable(instance.baseDir(), instance.page())) {
                    languages.addAll(instance.openLanguageIsoCode(slopTable).getDictionary());
                }
            }

            return languages;
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to read langauges from index journal");
        }
    }
}
