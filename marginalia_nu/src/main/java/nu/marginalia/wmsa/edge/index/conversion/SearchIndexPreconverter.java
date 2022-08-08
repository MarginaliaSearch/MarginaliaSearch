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
    };

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

        ShardOutput[] outputs = outputFiles.entrySet().stream().map(entry -> ShardOutput.fromFile(entry.getKey(), entry.getValue())).toArray(ShardOutput[]::new);

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

                for (int i = 0; i < outputs.length; i++) {
                    if (outputs[i].shard.block == entry.header.block().id
                        && partitioner.filterUnsafe(domainId, outputs[i].shard.bucket))
                    {
                        buffer.flip();

                        while (buffer.position() < buffer.limit())
                            outputs[i].fc.write(buffer);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
        logger.info("Finalizing preconversion");

        for (int i = 0; i < outputs.length; i++) {
            long pos = outputs[i].raf.getFilePointer();
            outputs[i].raf.seek(0);
            outputs[i].raf.writeLong(pos);
            outputs[i].raf.writeLong(wordCountOriginal);
            outputs[i].fc.force(true);
            outputs[i].fc.close();
            outputs[i].raf.close();
        }
    }

}

