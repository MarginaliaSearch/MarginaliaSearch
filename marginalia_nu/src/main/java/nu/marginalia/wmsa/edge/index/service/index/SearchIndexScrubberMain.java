package nu.marginalia.wmsa.edge.index.service.index;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class SearchIndexScrubberMain {
    public static final Logger logger = LoggerFactory.getLogger(SearchIndexScrubberMain.class);
    private static final int CHUNK_HEADER_SIZE = 16;

    public static void main(String... args) throws IOException {
        var inputFile = Path.of(args[0]).toFile();
        var outputFile = Path.of(args[1]).toFile();

        logger.info("Scrubbing {}", inputFile);

        final RandomAccessFile raf = new RandomAccessFile(inputFile, "r");

        var fileLength = raf.readLong();
        var wordCount = raf.readInt();

        logger.info("Word Count: {}", wordCount);
        logger.info("File Length: {}", fileLength);

        var channel = raf.getChannel();

        ByteBuffer inByteBuffer = ByteBuffer.allocateDirect(10_000);

        RandomAccessFile[] randomAccessFiles = new RandomAccessFile[1];

        for (int i = 0; i < randomAccessFiles.length; i++) {
            randomAccessFiles[i] = new RandomAccessFile(outputFile, "rw");
            randomAccessFiles[i].seek(12);
        }
        FileChannel[] fileChannels = new FileChannel[1];
        for (int i = 0; i < fileChannels.length; i++) {
            fileChannels[i] = randomAccessFiles[i].getChannel();
        }

        while (channel.position() < fileLength) {
            inByteBuffer.clear();
            inByteBuffer.limit(CHUNK_HEADER_SIZE);
            channel.read(inByteBuffer);
            inByteBuffer.flip();
            long urlId = inByteBuffer.getLong();
            int chunkBlock = inByteBuffer.getInt();
            int count = inByteBuffer.getInt();
            inByteBuffer.clear();
            inByteBuffer.limit(count*4+CHUNK_HEADER_SIZE);
            inByteBuffer.putLong(urlId);
            inByteBuffer.putInt(chunkBlock);
            inByteBuffer.putInt(count);
            channel.read(inByteBuffer);


            if (chunkBlock == IndexBlock.Link.id) {
                for (int i = 0; i < randomAccessFiles.length; i++) {
                    inByteBuffer.flip();
                    fileChannels[i].write(inByteBuffer);
                }
            }

        }

        long size = randomAccessFiles[0].getFilePointer();

        randomAccessFiles[0].seek(0);
        randomAccessFiles[0].writeLong(size);
        randomAccessFiles[0].writeInt(wordCount);

        randomAccessFiles[0].close();
    }
}
