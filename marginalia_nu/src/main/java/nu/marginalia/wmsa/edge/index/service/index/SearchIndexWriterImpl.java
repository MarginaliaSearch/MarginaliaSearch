package nu.marginalia.wmsa.edge.index.service.index;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.dictionary.DictionaryWriter;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SearchIndexWriterImpl implements SearchIndexWriter {
    private final DictionaryWriter dictionaryWriter;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Disposable writerTask;
    private RandomAccessFile raf;
    private FileChannel channel;

    public static final int MAX_BLOCK_SIZE = 1000*32*8*4;
    private final ByteBuffer byteBuffer;
    private long pos;

    @SneakyThrows
    public SearchIndexWriterImpl(DictionaryWriter dictionaryWriter, File indexFile) {
        this.dictionaryWriter = dictionaryWriter;
        initializeIndexFile(indexFile);

        byteBuffer = ByteBuffer.allocate(MAX_BLOCK_SIZE);

        writerTask = Schedulers.io().schedulePeriodicallyDirect(this::forceWrite, 1, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::forceWrite));
    }

    private void initializeIndexFile(File indexFile) throws IOException {
        raf = new RandomAccessFile(indexFile, "rw");
        channel = raf.getChannel();

        try {
            pos = raf.readLong();
            raf.seek(pos);
            logger.info("Resuming index file of size {}", pos);
        }
        catch (EOFException ex) {
            logger.info("Clean index file");
            writePositionMarker();
            writePositionMarker();
        }
    }

    @Override
    @SneakyThrows
    public synchronized void put(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, IndexBlock block, List<String> wordsSuspect) {
        int numGoodWords = 0;
        for (String word : wordsSuspect) {
            if (word.length() < Byte.MAX_VALUE) numGoodWords++;
        }

        byteBuffer.clear();
        long url_id = ((long) domainId.getId() << 32) | urlId.getId();
        byteBuffer.putLong(url_id);
        byteBuffer.putInt(block.id);
        byteBuffer.putInt(numGoodWords);

        for (String word : wordsSuspect) {
            if (word.length() < Byte.MAX_VALUE) {
                byteBuffer.putInt(dictionaryWriter.get(word));
            }
        }
        byteBuffer.limit(byteBuffer.position());
        byteBuffer.rewind();

        while (byteBuffer.position() < byteBuffer.limit())
            channel.write(byteBuffer);

        writePositionMarker();
    }

    @Override
    public synchronized void forceWrite() {
        try {
            channel.force(false);
        }
        catch (IOException ex) {
            logger.error("IO Exception", ex);
        }
    }


    @Override
    public void flushWords() {
        dictionaryWriter.commitToDisk();
    }

    private void writePositionMarker() throws IOException {
        var lock = channel.lock(0, 12, false);
        pos = channel.size();
        raf.seek(0);
        raf.writeLong(pos);
        raf.writeInt(dictionaryWriter.size());
        raf.seek(pos);
        lock.release();
    }

    public synchronized void close() throws IOException {
        writerTask.dispose();
        channel.close();
        raf.close();
    }
}
