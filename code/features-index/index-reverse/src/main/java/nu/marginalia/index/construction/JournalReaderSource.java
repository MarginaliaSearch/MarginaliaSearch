package nu.marginalia.index.construction;

import nu.marginalia.index.journal.reader.IndexJournalReader;

import java.io.IOException;
import java.nio.file.Path;

public interface JournalReaderSource {
    IndexJournalReader construct(Path sourceFile) throws IOException;
}
