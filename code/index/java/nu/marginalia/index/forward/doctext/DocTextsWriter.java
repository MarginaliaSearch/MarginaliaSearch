package nu.marginalia.index.forward.doctext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;


public class DocTextsWriter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DocTextsWriter.class);

    private final FileChannel outputChannel;

    public DocTextsWriter(Path outputFile) throws IOException {
        this.outputChannel = (FileChannel) Files.newByteChannel(outputFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /** Write blob to file
     *
     * @return encoded offset in file
     */
    public long write(byte[] blob) throws IOException {
        if (blob.length == 0) {
            return DocTextsCodec.encodeAbsent();
        }
        if (blob.length > DocTextsCodec.MAX_BLOB_SIZE) {
            logger.warn("Dropping document text blob of implausible size {}", blob.length);
            return DocTextsCodec.encodeAbsent();
        }

        long startOffset = outputChannel.position();

        ByteBuffer buffer = ByteBuffer.wrap(blob);
        while (buffer.hasRemaining()) {
            outputChannel.write(buffer);
        }

        return DocTextsCodec.encode(startOffset, blob.length);
    }

    @Override
    public void close() throws IOException {
        outputChannel.close();
    }
}
