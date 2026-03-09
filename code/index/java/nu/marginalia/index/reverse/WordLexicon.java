package nu.marginalia.index.reverse;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReaderIf;
import nu.marginalia.btree.legacy.LegacyBTreeReader;
import nu.marginalia.btree.paged.PagedBTreeReader;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.config.ReverseIndexParameters;

import java.io.IOException;
import java.nio.file.Path;

public class WordLexicon {
    public final String languageIsoCode;
    private final BTreeReaderIf reader;
    private final AutoCloseable resource;

    private WordLexicon(String languageIsoCode, BTreeReaderIf reader, AutoCloseable resource) {
        this.languageIsoCode = languageIsoCode;
        this.reader = reader;
        this.resource = resource;
    }

    /** Open a legacy mmap-backed B-tree word lexicon. */
    public static WordLexicon openLegacy(String languageIsoCode, Path fileName) throws IOException {
        LongArray words = LongArrayFactory.mmapForReadingShared(fileName);
        LinuxSystemCalls.madviseRandom(words.getMemorySegment());
        LegacyBTreeReader reader = new LegacyBTreeReader(words, ReverseIndexParameters.wordsBTreeContext, 0);
        return new WordLexicon(languageIsoCode, reader, words::close);
    }

    /** Open a paged B+-tree word lexicon using buffered reads via the OS page cache. */
    public static WordLexicon openBuffered(String languageIsoCode, Path fileName) throws IOException {
        PagedBTreeReader reader = PagedBTreeReader.buffered(fileName);
        return new WordLexicon(languageIsoCode, reader, reader);
    }

    /** Open a paged B+-tree word lexicon using O_DIRECT with a user-space buffer pool. */
    public static WordLexicon openDirect(String languageIsoCode, Path fileName, int poolSizePages) throws IOException {
        PagedBTreeReader reader = PagedBTreeReader.direct(fileName, poolSizePages);
        return new WordLexicon(languageIsoCode, reader, reader);
    }

    /** Return the format version of the underlying B-tree. */
    public int formatVersion() {
        return reader.formatVersion();
    }

    /** Calculate the offset of the word in the documents.
     * If the return-value is negative, the term does not exist
     * in the index.
     */
    public long wordOffset(long termId) {
        return reader.getValue(termId);
    }

    public void close() {
        try {
            resource.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
