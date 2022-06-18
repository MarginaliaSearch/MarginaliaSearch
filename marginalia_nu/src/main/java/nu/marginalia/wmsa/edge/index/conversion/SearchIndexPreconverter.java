package nu.marginalia.wmsa.edge.index.conversion;

import com.google.inject.Inject;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;
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

        SearchIndexJournalReader indexJournalReader = new SearchIndexJournalReader(MultimapFileLong.forReading(inputFile.toPath()));

        final long wordCountOriginal = indexJournalReader.fileHeader.wordCount();

        logger.info("{}", indexJournalReader.fileHeader);

        RandomAccessFile[] randomAccessFiles = new RandomAccessFile[outputFiles.length];
        for (int i = 0; i < randomAccessFiles.length; i++) {
            randomAccessFiles[i] = new RandomAccessFile(outputFiles[i], "rw");
            randomAccessFiles[i].seek(SearchIndexJournalReader.FILE_HEADER_SIZE_BYTES);
        }
        FileChannel[] fileChannels = new FileChannel[outputFiles.length];
        for (int i = 0; i < fileChannels.length; i++) {
            fileChannels[i] = randomAccessFiles[i].getChannel();
        }


        var lock = partitioner.getReadLock();
        try {
            lock.lock();
            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

            for (var entry : indexJournalReader) {
                if (!partitioner.isGoodUrl(entry.urlId())
                    || spamDomains.contains(entry.domainId())) {
                    continue;
                }

                int domainId = entry.domainId();
                buffer.clear();
                entry.copyToBuffer(buffer);

                for (int i = 0; i < randomAccessFiles.length; i++) {
                    if (partitioner.filterUnsafe(domainId, i)) {
                        buffer.flip();

                        while (buffer.position() < buffer.limit())
                            fileChannels[i].write(buffer);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }

        for (int i = 0; i < randomAccessFiles.length; i++) {
            long pos = randomAccessFiles[i].getFilePointer();
            randomAccessFiles[i].seek(0);
            randomAccessFiles[i].writeLong(pos);
            randomAccessFiles[i].writeLong(wordCountOriginal);
            fileChannels[i].force(true);
            fileChannels[i].close();
            randomAccessFiles[i].close();
        }
    }

}

