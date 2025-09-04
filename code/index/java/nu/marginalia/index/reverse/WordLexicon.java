package nu.marginalia.index.reverse;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.config.ReverseIndexParameters;

import java.io.IOException;
import java.nio.file.Path;

public class WordLexicon {
    public final String languageIsoCode;
    private final LongArray words;
    private final BTreeReader wordsBTreeReader;
    private final long wordsDataOffset;

    public WordLexicon(String languageIsoCode, Path fileName) throws IOException {
        this.languageIsoCode = languageIsoCode;


        this.words = LongArrayFactory.mmapForReadingShared(fileName);

        LinuxSystemCalls.madviseRandom(this.words.getMemorySegment());

        this.wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        this.wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();
    }

    /** Calculate the offset of the word in the documents.
     * If the return-value is negative, the term does not exist
     * in the index.
     */
    public long wordOffset(long termId) {
        long idx = wordsBTreeReader.findEntry(termId);

        if (idx < 0)
            return -1L;

        return words.get(wordsDataOffset + idx + 1);
    }

    public void close() {
        words.close();
    }
}
