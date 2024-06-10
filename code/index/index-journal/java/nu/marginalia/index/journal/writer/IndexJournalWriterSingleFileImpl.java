package nu.marginalia.index.journal.writer;

import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.reader.IndexJournalReader;
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

    private static final int ZSTD_BUFFER_SIZE = 8192;
    private static final int DATA_BUFFER_SIZE = 8192;

    private final MurmurHash3_128 hasher = new MurmurHash3_128();

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
        final var positions = data.positions();

        int recordSize = 0; // document header size is 3 longs
        for (int i = 0; i < keywords.length; i++) {
            // term header size is 2 longs
            recordSize += IndexJournalReader.TERM_HEADER_SIZE_BYTES + positions[i].bufferSize();
        }

        if (recordSize > Short.MAX_VALUE) {
            // This should never happen, but if it does, we should log it and deal with it in a way that doesn't corrupt the file
            // (32 KB is *a lot* of data for a single document, larger than the uncompressed HTML of most documents)
            logger.error("Omitting entry: Record size {} exceeds maximum representable size of {}", recordSize, Short.MAX_VALUE);
            return 0;
        }

        if (dataBuffer.capacity() - dataBuffer.position() < 3*8) {
            dataBuffer.flip();
            compressingStream.compress(dataBuffer);
            dataBuffer.clear();
        }

        dataBuffer.putShort((short) recordSize);
        dataBuffer.putShort((short) Math.clamp(header.documentSize(), 0, Short.MAX_VALUE));
        dataBuffer.putInt(header.documentFeatures());
        dataBuffer.putLong(header.combinedId());
        dataBuffer.putLong(header.documentMeta());

        for (int i = 0; i < keywords.length; i++) {
            int requiredSize = IndexJournalReader.TERM_HEADER_SIZE_BYTES + positions[i].bufferSize();

            if (dataBuffer.capacity() - dataBuffer.position() < requiredSize) {
                dataBuffer.flip();
                compressingStream.compress(dataBuffer);
                dataBuffer.clear();
            }

            dataBuffer.putLong(keywords[i]);
            dataBuffer.putShort((short) metadata[i]);
            dataBuffer.put((byte) positions[i].bufferSize());
            dataBuffer.put(positions[i].buffer());
        }

        numEntries++;

        return recordSize;
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
