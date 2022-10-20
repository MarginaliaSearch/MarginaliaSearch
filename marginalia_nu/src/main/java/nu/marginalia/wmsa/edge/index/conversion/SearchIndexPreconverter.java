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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

public class SearchIndexPreconverter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Shard(int bucket, int block) {}

    @SneakyThrows
    @Inject
    public SearchIndexPreconverter(File inputFile,
                                   Map<Shard, File> outputFiles,
                                   SearchIndexPartitioner partitioner,
                                   EdgeDomainBlacklist blacklist)
    {
        TIntHashSet spamDomains = blacklist.getSpamDomains();
        logger.info("Preconverting {}", inputFile);

        for (File f : outputFiles.values()) {
            if (f.exists()) {
                Files.deleteIfExists(Objects.requireNonNull(f).toPath());
            }
        }

        SearchIndexJournalReader indexJournalReader = new SearchIndexJournalReader(MultimapFileLong.forReading(inputFile.toPath()));

        final long wordCountOriginal = indexJournalReader.fileHeader.wordCount();

        logger.info("{}", indexJournalReader.fileHeader);

        ShardOutput[] outputs = outputFiles.entrySet().stream()
                .map(entry -> ShardOutput.fromFile(entry.getKey(), entry.getValue()))
                .toArray(ShardOutput[]::new);

        var lock = partitioner.getReadLock();
        try {
            lock.lock();
            ByteBuffer buffer = ByteBuffer.allocateDirect(65536);
            for (var entry : indexJournalReader) {
                if (!partitioner.isGoodUrl(entry.urlId())
                    || spamDomains.contains(entry.domainId())) {
                    continue;
                }

                buffer.clear();
                entry.copyToBuffer(buffer);

                for (ShardOutput output : outputs) {
                    if (output.shouldWrite(partitioner, entry)) {
                        buffer.flip();

                        output.write(buffer);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
        logger.info("Finalizing preconversion");

        for (ShardOutput output : outputs) {
            output.finish(wordCountOriginal);
        }
    }

    private record ShardOutput(Shard shard, RandomAccessFile raf, FileChannel fc) {
        public static ShardOutput fromFile(Shard s, File f) {
            try {
                var v = new RandomAccessFile(f, "rw");
                v.seek(SearchIndexJournalReader.FILE_HEADER_SIZE_BYTES);
                return new ShardOutput(s, v, v.getChannel());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean shouldWrite(SearchIndexPartitioner partitioner, SearchIndexJournalReader.JournalEntry entry) {
            return shard.block == entry.header.block().ordinal()
                    && partitioner.filterUnsafe(entry.domainId(), shard.bucket);
        }

        public void finish(long wordCountOriginal) throws IOException {
            long pos = raf.getFilePointer();
            raf.seek(0);
            raf.writeLong(pos);
            raf.writeLong(wordCountOriginal);
            fc.force(true);
            fc.close();
            raf.close();
        }

        public void write(ByteBuffer buffer) throws IOException {
            while (buffer.position() < buffer.limit())
                fc.write(buffer);
        }
    };

}

