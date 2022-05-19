package nu.marginalia.wmsa.edge.index.service.index.wordstable;

import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.function.LongConsumer;

public abstract class IndexWordsTable implements AutoCloseable {
    final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int BUFFER_SIZE = 1024*1024*64;

    public static IndexWordsTable ofFile(RandomAccessFile file) throws IOException {
        var wordsFile = openWordsFile(file);
        long signature = wordsFile.get(0);

        if (signature == Strategy.BTREE.ordinal()) {
            return new BtreeWordsTable(wordsFile);
        }
        throw new IllegalArgumentException("Unknown signature " + signature);
    }

    private static MultimapFileLong openWordsFile(RandomAccessFile wordsFile) throws IOException {
        return new MultimapFileLong(wordsFile,
                FileChannel.MapMode.READ_ONLY, wordsFile.length(), BUFFER_SIZE, false);
    }

    public abstract long positionForWord(int wordId);

    public abstract int wordLength(int wordId);
    public abstract void forEachWordsOffset(LongConsumer offsetConsumer);

    @Override
    public void close() throws Exception {

    }

    public record TableWordRange(long start, long end) {}

    public enum Strategy {
        FLAT, HASH, BTREE_OLD, BTREE
    }

}
