package nu.marginalia.lexicon.journal;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class KeywordLexiconJournalFile implements AutoCloseable {
    private final RandomAccessFile journalFileRAF;
    private final File journalFile;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ReadWriteLock diskLock = new ReentrantReadWriteLock();

    public KeywordLexiconJournalFile(File journalFile) throws IOException {
        this.journalFileRAF = new RandomAccessFile(journalFile, "rw");
        this.journalFile = journalFile;
    }

    public void loadFile(Consumer<byte[]> acceptEntry) throws IOException {
        if (!journalFile.exists()) {
            logger.info("File {} does not exist, can't load", journalFile);
            return;
        }

        logger.info("Reading {}", journalFile);

        long pos;
        if (journalFileRAF.length() < 8) {
            pos = 8;
            journalFileRAF.writeLong(pos);
        }
        else {
            pos = journalFileRAF.readLong();
        }

        logger.info("Length {} ({})", pos, journalFileRAF.length());
        if (pos == 8) {
            logger.info("Empty DB");
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

        var channel = journalFileRAF.getChannel();

        long cp = channel.position();
        try {
            buffer.limit(0);
            long loaded = 0;

            while (cp < pos || buffer.hasRemaining()) {
                if (buffer.limit() - buffer.position() < 4) {
                    buffer.compact();

                    long rb = channel.read(buffer);
                    if (rb <= 0) {
                        break;
                    }
                    cp += rb;
                    buffer.flip();
                }

                int len = buffer.get() & 0xFF;
                if (len > Byte.MAX_VALUE) {
                    logger.warn("Found keyword with impossible length {} near {}, likely corruption", len, cp);
                }
                while (buffer.limit() - buffer.position() < len) {
                    buffer.compact();
                    int rb = channel.read(buffer);
                    if (rb <= 0) break;
                    cp += rb;
                    buffer.flip();
                }

                if (buffer.limit() < len) {
                    logger.warn("Partial write at end-of-file!");

                    if (cp >= pos) {
                        logger.info("... but it's ok");
                    }
                    break;
                }

                byte[] data = new byte[len];
                buffer.get(data);
                if ((++loaded % 10_000_000) == 0L) {
                    logger.info("Loaded {} million items", loaded/1_000_000);
                }

                acceptEntry.accept(data);
            }
        }
        catch (Exception ex) {
            logger.error("IO Exception", ex);
        }

        journalFileRAF.seek(pos);
    }

    private final ByteBuffer writeBuffer = ByteBuffer.allocateDirect(4096);

    public void writeEntriesToJournal(List<byte[]> data) {
        if (data.isEmpty())
            return;

        final FileChannel channel = journalFileRAF.getChannel();

        if (!channel.isOpen()) {
            throw new IllegalStateException("commitToDisk() with closed channel! Cannot commit!");
        }

        Lock writeLock = diskLock.writeLock();
        try {
            writeLock.lock();

            long start = System.currentTimeMillis();
            int ct = data.size();

            for (byte[] itemBytes : data) {
                writeBuffer.clear();
                writeBuffer.put((byte) itemBytes.length);
                writeBuffer.put(itemBytes);
                writeBuffer.flip();

                while (writeBuffer.position() < writeBuffer.limit())
                    channel.write(writeBuffer, channel.size());
            }

            writeBuffer.clear();
            writeBuffer.putLong(channel.size());
            writeBuffer.flip();
            channel.write(writeBuffer, 0);

            channel.force(false);

            logger.debug("Comitted {} items in {} ms", ct, System.currentTimeMillis() - start);
        }
        catch (Exception ex) {
            logger.error("Error during dictionary commit!!!", ex);
        }
        finally {
            writeLock.unlock();
        }
    }

    public void close() throws IOException {
        journalFileRAF.close();
    }
}
