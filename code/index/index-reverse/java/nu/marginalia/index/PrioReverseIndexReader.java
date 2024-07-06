package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.EntrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PrioReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;
    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;
    private final String name;

    public PrioReverseIndexReader(String name,
                                  Path words,
                                  Path documents) throws IOException {
        this.name = name;

        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.documents = null;
            this.wordsBTreeReader = null;
            this.wordsDataOffset = -1;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArrayFactory.mmapForReadingShared(words);
        this.documents = LongArrayFactory.mmapForReadingShared(documents);

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();

    }

    /** Calculate the offset of the word in the documents.
     * If the return-value is negative, the term does not exist
     * in the index.
     */
    long wordOffset(long termId) {
        long idx = wordsBTreeReader.findEntry(termId);

        if (idx < 0)
            return -1L;

        return words.get(wordsDataOffset + idx + 1);
    }

    public EntrySource documents(long termId) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource();

        return new ReverseIndexEntrySource(name, createReaderNew(offset), 1, termId);
    }

    /** Return the number of documents with the termId in the index */
    public int numDocuments(long termId) {
        long offset = wordOffset(termId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    /** Create a BTreeReader for the document offset associated with a termId */
    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(
                documents,
                ReverseIndexParameters.prioDocsBTreeContext,
                offset);
    }

    public void close() {
        if (documents != null)
            documents.close();

        if (words != null)
            words.close();
    }

}
