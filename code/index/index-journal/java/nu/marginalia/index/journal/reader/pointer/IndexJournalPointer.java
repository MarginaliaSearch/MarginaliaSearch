package nu.marginalia.index.journal.reader.pointer;

import nu.marginalia.index.journal.model.IndexJournalEntryTermData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.LongPredicate;

/**
 * This is something like a double iterator.  The Index Journal consists of
 * blocks of words and word-metadata for each document and document metadata.
 * <br>
 *
 * Perhaps best conceptualized as something like
 *
 * <pre>[doc1: word1 word2 word3 word4] [doc2: word1 word2 word3 ]</pre>
 * nextDocument() will move the pointer from doc1 to doc2;<br>
 * nextRecord() will move the pointer from word1 to word2...<br>
 */
public interface IndexJournalPointer extends Iterable<IndexJournalEntryTermData>, AutoCloseable {
    /**
     * Advance to the next document in the journal,
     * returning true if such a document exists.
     * Resets the record index to before the first
     * record (if it exists).
     */
    boolean nextDocument();

    /**
     * Get the id associated with the current document
     */
    long documentId();

    /**
     * Get the metadata associated with the current document
     */
    long documentMeta();

    /**
     * Get the documentFeatures associated with the current record
     */
    int documentFeatures();

    int documentSize();

    /** Concatenate a number of journal pointers */
    static IndexJournalPointer concatenate(IndexJournalPointer... pointers) {
        if (pointers.length == 1)
            return pointers[0];

        return new JoiningJournalPointer(pointers);
    }

    /** Add a filter on word metadata to the pointer */
    default IndexJournalPointer filterWordMeta(LongPredicate filter) {
        return new FilteringJournalPointer(this, filter);
    }

    void close() throws IOException;
}

class JoiningJournalPointer implements IndexJournalPointer {
    private final IndexJournalPointer[] pointers;
    private int pIndex = 0;

    JoiningJournalPointer(IndexJournalPointer[] pointers) {
        this.pointers = pointers;
    }

    @Override
    public boolean nextDocument() {

        while (pIndex < pointers.length) {
            if (pointers[pIndex].nextDocument())
                return true;
            else pIndex++;
        }

        return false;
    }

    @Override
    public long documentId() {
        return pointers[pIndex].documentId();
    }

    @Override
    public long documentMeta() {
        return pointers[pIndex].documentMeta();
    }


    @Override
    public int documentFeatures() {
        return pointers[pIndex].documentFeatures();
    }

    @Override
    public int documentSize() {
        return pointers[pIndex].documentSize();
    }

    @NotNull
    @Override
    public Iterator<IndexJournalEntryTermData> iterator() {
        return pointers[pIndex].iterator();
    }

    public void close() {
        for (var p : pointers) {
            try {
                p.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}

class FilteringJournalPointer implements IndexJournalPointer {
    private final IndexJournalPointer base;
    private final LongPredicate filter;

    FilteringJournalPointer(IndexJournalPointer base, LongPredicate filter) {
        this.base = base;
        this.filter = filter;
    }

    @Override
    public boolean nextDocument() {
        while (base.nextDocument()) {
            if (iterator().hasNext()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long documentId() {
        return base.documentId();
    }

    @Override
    public long documentMeta() {
        return base.documentMeta();
    }

    @Override
    public int documentFeatures() {
        return base.documentFeatures();
    }


    @Override
    public int documentSize() {
        return base.documentSize();
    }

    @NotNull
    @Override
    public Iterator<IndexJournalEntryTermData> iterator() {

        return new Iterator<>() {
            private final Iterator<IndexJournalEntryTermData> baseIter = base.iterator();
            private IndexJournalEntryTermData value = null;

            @Override
            public boolean hasNext() {
                if (value != null) {
                    return true;
                }
                while (baseIter.hasNext()) {
                    value = baseIter.next();
                    if (filter.test(value.metadata())) {
                        return true;
                    }
                }
                value = null;
                return false;
            }

            @Override
            public IndexJournalEntryTermData next() {
                if (hasNext()) {
                    var ret = value;
                    value = null;
                    return ret;
                } else {
                    throw new IllegalStateException("No more elements");
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        base.close();
    }
}