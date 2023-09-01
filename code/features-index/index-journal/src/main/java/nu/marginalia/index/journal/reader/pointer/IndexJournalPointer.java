package nu.marginalia.index.journal.reader.pointer;

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
public interface IndexJournalPointer {
    /**
     * Advance to the next document in the journal,
     * returning true if such a document exists.
     * Resets the record index to before the first
     * record (if it exists).
     */
    boolean nextDocument();

    /**
     * Advance to the next record in the journal
     */
    boolean nextRecord();

    /**
     * Get the id associated with the current document
     */
    long documentId();

    /**
     * Get the metadata associated with the current document
     */
    long documentMeta();

    /**
     * Get the wordId associated with the current record
     */
    long wordId();

    /**
     * Get the termMeta associated with the current record
     */
    long wordMeta();

    /**
     * Get the documentFeatures associated with the current record
     */
    int documentFeatures();

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
    public boolean nextRecord() {
        return pointers[pIndex].nextRecord();
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
    public long wordId() {
        return pointers[pIndex].wordId();
    }

    @Override
    public long wordMeta() {
        return pointers[pIndex].wordMeta();
    }

    @Override
    public int documentFeatures() {
        return pointers[pIndex].documentFeatures();
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
        return base.nextDocument();
    }

    @Override
    public boolean nextRecord() {
        while (base.nextRecord()) {
            if (filter.test(wordMeta()))
                return true;
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
    public long wordId() {
        return base.wordId();
    }

    @Override
    public long wordMeta() {
        return base.wordMeta();
    }

    @Override
    public int documentFeatures() {
        return base.documentFeatures();
    }
}