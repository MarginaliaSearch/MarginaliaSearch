package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.EntrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class PrioReverseIndexReader {
    private final LongArray words;
    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;
    private final String name;

    private final FileChannel documentsChannel;

    public PrioReverseIndexReader(String name,
                                  Path words,
                                  Path documents) throws IOException {
        this.name = name;

        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.wordsBTreeReader = null;
            this.documentsChannel = null;
            this.wordsDataOffset = -1;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArrayFactory.mmapForReadingShared(words);

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();

        documentsChannel = (FileChannel) Files.newByteChannel(documents);
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

        return new PrioIndexEntrySource(name,
                documentsChannel,
                offset,
                termId);
    }

    /** Return the number of documents with the termId in the index */
    public int numDocuments(long termId) {

        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return 0;

        ByteBuffer buffer = ByteBuffer.allocate(4);
        try {
            documentsChannel.read(buffer, offset);
        }
        catch (IOException e) {
            logger.error("Failed to read documents channel", e);
            return 0;
        }

        return buffer.getInt(0) & 0x3FFF_FFFF;

    }


    public void close() {
        try {
            documentsChannel.close();
        }
        catch (IOException e) {
            logger.error("Failed to close documents channel", e);
        }

        if (words != null)
            words.close();
    }

}
