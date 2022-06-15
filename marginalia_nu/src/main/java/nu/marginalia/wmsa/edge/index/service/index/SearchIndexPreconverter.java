package nu.marginalia.wmsa.edge.index.service.index;

import com.google.inject.Inject;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Objects;

public class SearchIndexPreconverter {
    private static final int CHUNK_HEADER_SIZE = 16;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SearchIndexPartitioner partitioner;
    private final TIntHashSet spamDomains;

    @SneakyThrows
    public static long wordCount(File inputFile) {
        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
            raf.readLong();
            return raf.readInt();
        }
    }

    @SneakyThrows
    @Inject
    public SearchIndexPreconverter(File inputFile,
                                   File[] outputFiles,
                                   SearchIndexPartitioner partitioner,
                                   EdgeDomainBlacklist blacklist)
    {
        this.partitioner = partitioner;
        this.spamDomains = blacklist.getSpamDomains();
        logger.info("Preconverting {}", inputFile);

        for (File f : outputFiles) {
            if (f.exists()) {
                Files.deleteIfExists(Objects.requireNonNull(f).toPath());
            }
        }

        final RandomAccessFile raf = new RandomAccessFile(inputFile, "r");

        var fileLength = raf.readLong();
        var wordCount = raf.readInt();
        final int wordCountOriginal = wordCount;

        logger.info("Word Count: {}", wordCount);
        logger.info("File Length: {}", fileLength);

        var channel = raf.getChannel();

        ByteBuffer inByteBuffer = ByteBuffer.allocateDirect(10_000);

        RandomAccessFile[] randomAccessFiles = new RandomAccessFile[outputFiles.length];
        for (int i = 0; i < randomAccessFiles.length; i++) {
            randomAccessFiles[i] = new RandomAccessFile(outputFiles[i], "rw");
            randomAccessFiles[i].seek(12);
        }
        FileChannel[] fileChannels = new FileChannel[outputFiles.length];
        for (int i = 0; i < fileChannels.length; i++) {
            fileChannels[i] = randomAccessFiles[i].getChannel();
        }


        var lock = partitioner.getReadLock();
        try {
            lock.lock();

            while (channel.position() < fileLength) {
                inByteBuffer.clear();
                inByteBuffer.limit(CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);
                inByteBuffer.flip();
                long urlId = inByteBuffer.getLong();
                int chunkBlock = inByteBuffer.getInt();
                int count = inByteBuffer.getInt();
                //            inByteBuffer.clear();
                inByteBuffer.limit(count * 4 + CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);
                inByteBuffer.position(CHUNK_HEADER_SIZE);

                for (int i = 0; i < count; i++) {
                    wordCount = Math.max(wordCount, 1 + inByteBuffer.getInt());
                }

                inByteBuffer.position(count * 4 + CHUNK_HEADER_SIZE);


                if (isUrlAllowed(urlId)) {
                    for (int i = 0; i < randomAccessFiles.length; i++) {
                        if (partitioner.filterUnsafe(lock, (int) (urlId >>> 32L), i)) {
                            inByteBuffer.flip();
                            fileChannels[i].write(inByteBuffer);
                        }
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }

        if (wordCountOriginal < wordCount) {
            logger.warn("Raised word count {} => {}", wordCountOriginal, wordCount);
        }

        for (int i = 0; i < randomAccessFiles.length; i++) {
            long pos = randomAccessFiles[i].getFilePointer();
            randomAccessFiles[i].seek(0);
            randomAccessFiles[i].writeLong(pos);
            randomAccessFiles[i].writeInt(wordCount);
            fileChannels[i].force(true);
            fileChannels[i].close();
            randomAccessFiles[i].close();
        }
    }

    private boolean isUrlAllowed(long url) {
        int urlId = (int)(url & 0xFFFF_FFFFL);
        int domainId = (int)(url >>> 32);

        return partitioner.isGoodUrl(urlId) && !spamDomains.contains(domainId);
    }

}

