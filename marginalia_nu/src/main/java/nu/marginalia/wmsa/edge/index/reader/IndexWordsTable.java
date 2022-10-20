package nu.marginalia.wmsa.edge.index.reader;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.function.LongConsumer;

import static nu.marginalia.wmsa.edge.index.conversion.words.WordsTableWriter.wordsBTreeContext;

public class IndexWordsTable implements AutoCloseable {
    protected final MultimapFileLong words;
    protected final BTreeReader reader;
    protected final int HEADER_OFFSET = 1;
    final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int BUFFER_SIZE = 1024*1024*64;

    public IndexWordsTable(MultimapFileLong words) {
        this.words = words;

        reader = new BTreeReader(words, wordsBTreeContext, HEADER_OFFSET);

        madvise();
    }

    public static IndexWordsTable ofFile(RandomAccessFile file) throws IOException {
        var wordsFile = openWordsFile(file);
        long signature = wordsFile.get(0);

        if (signature == Strategy.BTREE.ordinal()) {
            return new IndexWordsTable(wordsFile);
        }

        throw new IllegalArgumentException("Unknown signature " + signature);
    }

    private static MultimapFileLong openWordsFile(RandomAccessFile wordsFile) throws IOException {
        return new MultimapFileLong(wordsFile,
                FileChannel.MapMode.READ_ONLY, wordsFile.length(), BUFFER_SIZE);
    }

    public long positionForWord(int wordId) {
        long offset = reader.findEntry(wordId);

        if (offset < 0) {
            return -1L;
        }

        return words.get(offset+1);
    }

    public int wordLength(int wordId) {

        long offset = reader.findEntry(wordId);
        if (offset < 0) {
            return -1;
        }

        return (int)(words.get(offset) >> 32);
    }

    protected void madvise() {
        words.advice(NativeIO.Advice.Random);
        words.advice0(NativeIO.Advice.WillNeed);

        var h = reader.getHeader();
        int length = (int)(h.dataOffsetLongs() - h.indexOffsetLongs());

        words.adviceRange(NativeIO.Advice.WillNeed, h.indexOffsetLongs(), length);
        words.pokeRange(h.indexOffsetLongs(), length);
    }

    public void forEachWordsOffset(LongConsumer offsetConsumer) {
        int n = reader.numEntries();
        long offset = reader.getHeader().dataOffsetLongs();

        for (int i = 0; i < n; i++) {
            try {
                long posOffset = 2*(offset + i);
                if (posOffset * 8 >= words.size()) {
                    break;
                }

                long sz = words.get(posOffset);
                if ((sz>> 32) > 0) {
                    offsetConsumer.accept(words.get(posOffset+1));
                }
            }
            catch (Exception ex) {
                logger.warn("Error @ " + i, ex);
                break;
            }
        }
    }

    @Override
    public void close() throws Exception {
        words.close();
    }

    public enum Strategy {
        BTREE
    }

}
