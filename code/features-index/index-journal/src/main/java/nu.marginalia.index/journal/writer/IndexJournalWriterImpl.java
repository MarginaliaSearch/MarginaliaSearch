package nu.marginalia.index.journal.writer;

import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import com.github.luben.zstd.ZstdOutputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.lexicon.KeywordLexicon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IndexJournalWriterImpl implements IndexJournalWriter{
    private final KeywordLexicon lexicon;

    private static final int ZSTD_BUFFER_SIZE = 8192;
    private static final int DATA_BUFFER_SIZE = 8192;

    private final ByteBuffer dataBuffer = ByteBuffer.allocateDirect(DATA_BUFFER_SIZE);


    private final ZstdDirectBufferCompressingStream compressingStream;
    private int numEntries = 0;
    private final FileChannel fileChannel;

    public IndexJournalWriterImpl(KeywordLexicon lexicon, Path outputFile) throws IOException {
        this.lexicon = lexicon;

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
    public synchronized void put(IndexJournalEntryHeader header, IndexJournalEntryData entry) {
        if (dataBuffer.capacity() - dataBuffer.position() < 3*8) {
            dataBuffer.flip();
            compressingStream.compress(dataBuffer);
            dataBuffer.clear();
        }

        dataBuffer.putInt(entry.size());
        dataBuffer.putInt(header.documentFeatures());
        dataBuffer.putLong(header.combinedId());
        dataBuffer.putLong(header.documentMeta());

        for (int i = 0; i < entry.size(); ) {
            int remaining = (dataBuffer.capacity() - dataBuffer.position()) / 8;
            if (remaining <= 0) {
                dataBuffer.flip();
                compressingStream.compress(dataBuffer);
                dataBuffer.clear();
            }
            else while (remaining-- > 0 && i < entry.size()) {
                dataBuffer.putLong(entry.underlyingArray[i++]);
            }
        }

        numEntries++;
    }

    public void close() throws IOException {
        dataBuffer.flip();
        compressingStream.compress(dataBuffer);
        dataBuffer.clear();
        compressingStream.flush();
        compressingStream.close();


        // Finalize the file by writing a header

        ByteBuffer header = ByteBuffer.allocate(16);
        header.putLong(numEntries);
        header.putLong(lexicon.size());
        header.flip();

        while (header.position() < header.limit()) {
            fileChannel.write(header, header.position());
        }

        fileChannel.close();
    }
}
