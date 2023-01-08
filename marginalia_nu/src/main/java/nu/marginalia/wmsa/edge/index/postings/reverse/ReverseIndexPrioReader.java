package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySource;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.wmsa.edge.index.query.EmptyEntrySource;
import nu.marginalia.wmsa.edge.index.query.EntrySource;
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

    public int numDocuments(int wordId) {
        if (wordId < 0)
            return 0;

        long offset = words.get(wordId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexParameters.bTreeContext, offset);
    }
}
