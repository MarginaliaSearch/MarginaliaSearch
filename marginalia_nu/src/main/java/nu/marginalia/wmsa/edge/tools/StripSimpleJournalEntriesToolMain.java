package nu.marginalia.wmsa.edge.tools;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalCleaner;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReadEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;

import java.io.IOException;
import java.nio.file.Path;

import static nu.marginalia.wmsa.edge.index.model.EdgePageDocumentFlags.Simple;

public class StripSimpleJournalEntriesToolMain {

    public static void main(String[] args) throws IOException {
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        new SearchIndexJournalCleaner(new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(input)))
                .clean(output, StripSimpleJournalEntriesToolMain::retainEntry);

        System.out.println("All done!");
    }

    private static boolean retainEntry(SearchIndexJournalReadEntry entry) {
        return (entry.header.documentMeta() & Simple.asBit()) == 0;
    }
}
