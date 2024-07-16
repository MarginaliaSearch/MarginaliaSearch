package nu.marginalia.index.journal.writer;

import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.sequence.CodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/** IndexJournalWriter implementation that creates a single journal file */
public class IndexJournalWriterSingleFileImpl implements IndexJournalWriter{

    private static final int ZSTD_BUFFER_SIZE = 1<<16;
    private static final int DATA_BUFFER_SIZE = 1<<16;

    private final ByteBuffer dataBuffer = ByteBuffer.allocateDirect(DATA_BUFFER_SIZE);

    private final ZstdDirectBufferCompressingStream compressingStream;
    private final FileChannel fileChannel;

    private int numEntries = 0;
    private boolean closed = false;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public IndexJournalWriterSingleFileImpl(Path outputFile) throws IOException {

        logger.info("Creating Journal Writer {}", outputFile);

        Files.deleteIfExists(outputFile);
        Files.createFile(outputFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        fileChannel = FileChannel.open(outputFile, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        writeHeaderPlaceholder(fileChannel);

        compressingStream = new ZstdDirectBufferCompressingStream(ByteBuffer.allocateDirect(ZSTD_BUFFER_SIZE), 3) {
            protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
                toFlush.flip();
                while (toFlush.hasRemaining()) {
                    fileChannel.write(toFlush);
                }
                toFlush.clear();

                return toFlush;
            }
        };
    }

    /** The file has a non-compressed header at the beginning of the file.
     * Write a placeholder first to reserve the bytes, and position the
     * channel after the header
     */
    private static void writeHeaderPlaceholder(FileChannel fileStream) throws IOException {
        var buffer = ByteBuffer.allocate(IndexJournalReader.FILE_HEADER_SIZE_BYTES);

        buffer.position(0);
        buffer.limit(buffer.capacity());

        while (buffer.hasRemaining())
            fileStream.write(buffer, buffer.position());

        fileStream.position(IndexJournalReader.FILE_HEADER_SIZE_BYTES);
    }

    @Override
    @SneakyThrows
    public int put(IndexJournalEntryHeader header,
                   IndexJournalEntryData data)
    {
        final long[] keywords = data.termIds();
        final long[] metadata = data.metadata();
        final CodedSequence[] positions = data.positions();

        int entrySize = 0;
        for (var position : positions) {
            entrySize += IndexJournalReader.TERM_HEADER_SIZE_BYTES + position.bufferSize();
        }
        int totalSize = IndexJournalReader.DOCUMENT_HEADER_SIZE_BYTES + entrySize;

        if (entrySize > DATA_BUFFER_SIZE) {
            // This should never happen, but if it does, we should log it and deal with it in a way that doesn't corrupt the file
            // (64 KB is *a lot* of data for a single document, larger than the uncompressed HTML in like the 95%th percentile of web pages)
            logger.error("Omitting entry: Record size {} exceeds maximum representable size of {}", entrySize, DATA_BUFFER_SIZE);
            return 0;
        }

        if (dataBuffer.remaining() < totalSize) {
            dataBuffer.flip();
            compressingStream.compress(dataBuffer);
            dataBuffer.clear();
        }

        if (dataBuffer.remaining() < totalSize) {
            logger.error("Omitting entry: Record size {} exceeds buffer size of {}", totalSize, dataBuffer.capacity());
            return 0;
        }

        assert entrySize < (1 << 16) : "Entry size must not exceed USHORT_MAX";

        dataBuffer.putShort((short) entrySize);
        dataBuffer.putShort((short) Math.clamp(header.documentSize(), 0, Short.MAX_VALUE));
        dataBuffer.putInt(header.documentFeatures());
        dataBuffer.putLong(header.combinedId());
        dataBuffer.putLong(header.documentMeta());

        for (int i = 0; i < keywords.length; i++) {
            dataBuffer.putLong(keywords[i]);
            dataBuffer.putShort((short) metadata[i]);
            dataBuffer.putShort((short) positions[i].bufferSize());
            dataBuffer.put(positions[i].buffer());
        }

        numEntries++;

        return totalSize;
    }

    public void close() throws IOException {
        if (closed)
            return;
        else
            closed = true;

        dataBuffer.flip();
        compressingStream.compress(dataBuffer);
        dataBuffer.clear();
        compressingStream.flush();
        compressingStream.close();


        // Finalize the file by writing a header in the beginning
        ByteBuffer header = ByteBuffer.allocate(IndexJournalReader.FILE_HEADER_SIZE_BYTES);
        header.putLong(numEntries);
        header.putLong(0);  // reserved for future use
        header.flip();

        while (header.position() < header.limit()) {
            fileChannel.write(header, header.position());
        }

        fileChannel.close();
    }
}
