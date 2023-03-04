package nu.marginalia.index.reverse;

import nu.marginalia.index.reverse.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.index.reverse.query.ReverseIndexEntrySource;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.array.LongArray;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReverseIndexPrioReader {
    private final LongArray words;
    private final LongArray documents;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ReverseIndexPrioReader(Path words, Path documents) throws IOException {
        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.documents = null;
            return;
        }

        logger.info("Switching prio reverse index");

        this.words = LongArray.mmapRead(words);
        this.documents = LongArray.mmapRead(documents);
    }

    public EntrySource priorityDocuments(int wordId) {
        if (words == null) {
            // index not loaded
            return new EmptyEntrySource();
        }

        if (wordId < 0 || wordId >= words.size()) return new EmptyEntrySource();

        long offset = words.get(wordId);

        if (offset < 0) return new EmptyEntrySource();

        return new ReverseIndexEntrySource(createReaderNew(offset), ReverseIndexEntrySourceBehavior.DO_PREFER);
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexParameters.bTreeContext, offset);
    }
}
