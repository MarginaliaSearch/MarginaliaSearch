package nu.marginalia.index.priority;

import nu.marginalia.index.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.array.LongArray;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.ReverseIndexRetainFilter;
import nu.marginalia.index.query.filter.QueryFilterNoPass;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReverseIndexPriorityReader {
    private final LongArray words;
    private final LongArray documents;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ReverseIndexPriorityReader(Path words, Path documents) throws IOException {
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

        return new ReverseIndexPriorityEntrySource(createReaderNew(offset), ReverseIndexEntrySourceBehavior.DO_PREFER);
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexPriorityParameters.bTreeContext, offset);
    }

    public QueryFilterStepIf also(int wordId) {
        if (wordId < 0) return new QueryFilterNoPass();

        long offset = words.get(wordId);

        if (offset < 0) return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset));
    }

}
