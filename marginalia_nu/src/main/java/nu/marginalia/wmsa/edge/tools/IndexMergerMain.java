package nu.marginalia.wmsa.edge.tools;

import com.google.inject.Inject;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import nu.marginalia.util.ranking.RankingDomainFetcher;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexDao;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexPartitioner;
import org.mariadb.jdbc.Driver;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Objects;

public class IndexMergerMain {
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

    public static void main(String... args) {
        Driver driver = new Driver();

        File file1 = new File(args[0]);
        File file2 = new File(args[1]);
        File outputFile = new File(args[2]);

        if (!file1.exists()) {
            System.err.println("File " + file1 + " does not exist");
            return;
        }
        if (!file2.exists()) {
            System.err.println("File " + file2 + " does not exist");
            return;
        }

        if (outputFile.exists()) { // Footgun prevention
            System.err.println("File " + outputFile + " already exists");
            return;
        }

        var hikari = new DatabaseModule().provideConnection();
        var ds = new DatabaseModule().provideConnection();
        var domains = new RankingDomainFetcher(ds, new EdgeDomainBlacklistImpl(ds));
        var partitioner = new SearchIndexPartitioner(new SearchIndexDao(hikari, domains, new RankingSettings()));
        var blacklist = new EdgeDomainBlacklistImpl(hikari);

        new IndexMergerMain(file1, file2, outputFile, partitioner, blacklist);
    }


    @SneakyThrows
    @Inject
    public IndexMergerMain(File inputFile1, File inputFile2,
                                   File outputFile,
                                   SearchIndexPartitioner partitioner,
                                   EdgeDomainBlacklist blacklist)
    {
        this.partitioner = partitioner;
        this.spamDomains = blacklist.getSpamDomains();

        if (outputFile.exists()) {
            Files.deleteIfExists(Objects.requireNonNull(outputFile).toPath());
        }

        Roaring64Bitmap secondFileIndices = findIndices(inputFile2);

        RandomAccessFile randomAccessFile = new RandomAccessFile(outputFile, "rw");
        randomAccessFile.seek(12);

        FileChannel outputFileChannel = randomAccessFile.getChannel();

        int wc1 = copyToOutputFile(inputFile2, outputFileChannel, secondFileIndices, true);
        int wc2 = copyToOutputFile(inputFile1, outputFileChannel, secondFileIndices, false);

        long pos = randomAccessFile.getFilePointer();

        randomAccessFile.seek(0);
        randomAccessFile.writeLong(pos);
        randomAccessFile.writeInt(Math.max(wc1, wc2));
        outputFileChannel.force(true);
        outputFileChannel.close();
        randomAccessFile.close();
    }

    private Roaring64Bitmap findIndices(File file) throws IOException {
        Roaring64Bitmap ret = new Roaring64Bitmap();

        logger.info("Mapping indices in {}", file);

        try (final RandomAccessFile raf = new RandomAccessFile(file, "r"); var channel = raf.getChannel()) {

            var fileLength = raf.readLong();
            raf.readInt();

            ByteBuffer inByteBuffer = ByteBuffer.allocateDirect(10_000);

            while (channel.position() < fileLength) {
                inByteBuffer.clear();
                inByteBuffer.limit(CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);
                inByteBuffer.flip();
                long urlId = inByteBuffer.getLong();
                int chunkBlock = inByteBuffer.getInt();
                int count = inByteBuffer.getInt();
                inByteBuffer.limit(count * 4 + CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);

                ret.add(encodeId(urlId, chunkBlock));
            }
        }

        logger.info("Cardinality = {}", ret.getLongCardinality());

        return ret;
    }

    private int copyToOutputFile(File inFile, FileChannel outFile, Roaring64Bitmap urlIdAndBlock, boolean ifInSet) throws IOException {
        int wordCount = 0;

        logger.info("Copying from {}", inFile);
        long skippedWrongFile = 0;
        long skippedBadUrl = 0;
        try (final RandomAccessFile raf = new RandomAccessFile(inFile, "r"); var channel = raf.getChannel()) {

            var fileLength = raf.readLong();
            raf.readInt();

            ByteBuffer inByteBuffer = ByteBuffer.allocateDirect(10_000);

            while (channel.position() < fileLength) {
                inByteBuffer.clear();
                inByteBuffer.limit(CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);
                inByteBuffer.flip();
                long urlId = inByteBuffer.getLong();
                int chunkBlock = inByteBuffer.getInt();
                int count = inByteBuffer.getInt();
                inByteBuffer.limit(count*4+CHUNK_HEADER_SIZE);
                channel.read(inByteBuffer);
                inByteBuffer.position(CHUNK_HEADER_SIZE);

                for (int i = 0; i < count; i++) {
                    wordCount = Math.max(wordCount, 1+inByteBuffer.getInt());
                }

                inByteBuffer.position(count*4+CHUNK_HEADER_SIZE);

                if (urlIdAndBlock.contains(encodeId(urlId, chunkBlock)) == ifInSet) {
                    if (isUrlAllowed(urlId)) {
                        inByteBuffer.flip();

                        while (inByteBuffer.position() < inByteBuffer.limit())
                            outFile.write(inByteBuffer);
                    }
                    else {
                        skippedBadUrl++;
                    }
                }
                else {
                    skippedWrongFile++;
                }
            }

        }

        logger.info("Skipped {}, {}", skippedBadUrl, skippedWrongFile);
        return wordCount;
    }

    private long encodeId(long urlId, int chunkBlock) {
        return ((urlId & 0xFFFF_FFFFL) << 4L) | chunkBlock;
    }

    private boolean isUrlAllowed(long url) {
        int urlId = (int)(url & 0xFFFF_FFFFL);
        int domainId = (int)(url >>> 32);

        return partitioner.isGoodUrl(urlId) && !spamDomains.contains(domainId);
    }

}
