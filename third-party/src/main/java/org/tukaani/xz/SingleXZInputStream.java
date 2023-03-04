/*
 * SingleXZInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.EOFException;
import org.tukaani.xz.common.DecoderUtil;
import org.tukaani.xz.common.StreamFlags;
import org.tukaani.xz.index.IndexHash;
import org.tukaani.xz.check.Check;

/**
 * Decompresses exactly one XZ Stream in streamed mode (no seeking).
 * The decompression stops after the first XZ Stream has been decompressed,
 * and the read position in the input stream is left at the first byte
 * after the end of the XZ Stream. This can be useful when XZ data has
 * been stored inside some other file format or protocol.
 * <p>
 * Unless you know what you are doing, don't use this class to decompress
 * standalone .xz files. For that purpose, use <code>XZInputStream</code>.
 *
 * @see XZInputStream
 */
public class SingleXZInputStream extends InputStream {
    private InputStream in;
    private int memoryLimit;
    private StreamFlags streamHeaderFlags;
    private Check check;
    private BlockInputStream blockDecoder = null;
    private final IndexHash indexHash = new IndexHash();
    private boolean endReached = false;
    private IOException exception = null;

    /**
     * Creates a new input stream that decompresses exactly one XZ Stream
     * from <code>in</code>.
     * <p>
     * This constructor reads and parses the XZ Stream Header (12 bytes)
     * from <code>in</code>. The header of the first Block is not read
     * until <code>read</code> is called.
     *
     * @param       in          input stream from which XZ-compressed
     *                          data is read
     *
     * @throws      XZFormatException
     *                          input is not in the XZ format
     *
     * @throws      CorruptedInputException
     *                          XZ header CRC32 doesn't match
     *
     * @throws      UnsupportedOptionsException
     *                          XZ header is valid but specifies options
     *                          not supported by this implementation
     *
     * @throws      EOFException
     *                          less than 12 bytes of input was available
     *                          from <code>in</code>
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public SingleXZInputStream(InputStream in) throws IOException {
        initialize(in, -1);
    }

    /**
     * Creates a new single-stream XZ decompressor with optional
     * memory usage limit.
     * <p>
     * This is identical to <code>SingleXZInputStream(InputStream)</code>
     * except that this takes also the <code>memoryLimit</code> argument.
     *
     * @param       in          input stream from which XZ-compressed
     *                          data is read
     *
     * @param       memoryLimit memory usage limit as kibibytes (KiB)
     *                          or -1 to impose no memory usage limit
     *
     * @throws      XZFormatException
     *                          input is not in the XZ format
     *
     * @throws      CorruptedInputException
     *                          XZ header CRC32 doesn't match
     *
     * @throws      UnsupportedOptionsException
     *                          XZ header is valid but specifies options
     *                          not supported by this implementation
     *
     * @throws      EOFException
     *                          less than 12 bytes of input was available
     *                          from <code>in</code>
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public SingleXZInputStream(InputStream in, int memoryLimit)
            throws IOException {
        initialize(in, memoryLimit);
    }

    SingleXZInputStream(InputStream in, int memoryLimit,
                        byte[] streamHeader) throws IOException {
        initialize(in, memoryLimit, streamHeader);
    }

    private void initialize(InputStream in, int memoryLimit)
            throws IOException {
        byte[] streamHeader = new byte[DecoderUtil.STREAM_HEADER_SIZE];
        new DataInputStream(in).readFully(streamHeader);
        initialize(in, memoryLimit, streamHeader);
    }

    private void initialize(InputStream in, int memoryLimit,
                            byte[] streamHeader) throws IOException {
        this.in = in;
        this.memoryLimit = memoryLimit;
        streamHeaderFlags = DecoderUtil.decodeStreamHeader(streamHeader);
        check = Check.getInstance(streamHeaderFlags.checkType);
    }

    /**
     * Gets the ID of the integrity check used in this XZ Stream.
     *
     * @return      the Check ID specified in the XZ Stream Header
     */
    public int getCheckType() {
        return streamHeaderFlags.checkType;
    }

    /**
     * Gets the name of the integrity check used in this XZ Stream.
     *
     * @return      the name of the check specified in the XZ Stream Header
     */
    public String getCheckName() {
        return check.getName();
    }

    /**
     * Decompresses the next byte from this input stream.
     * <p>
     * Reading lots of data with <code>read()</code> from this input stream
     * may be inefficient. Wrap it in <code>java.io.BufferedInputStream</code>
     * if you need to read lots of data one byte at a time.
     *
     * @return      the next decompressed byte, or <code>-1</code>
     *              to indicate the end of the compressed stream
     *
     * @throws      CorruptedInputException
     * @throws      UnsupportedOptionsException
     * @throws      MemoryLimitException
     *
     * @throws      EOFException
     *                          compressed input is truncated or corrupt
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public int read() throws IOException {
        byte[] buf = new byte[1];
        return read(buf, 0, 1) == -1 ? -1 : (buf[0] & 0xFF);
    }

    /**
     * Decompresses into an array of bytes.
     * <p>
     * If <code>len</code> is zero, no bytes are read and <code>0</code>
     * is returned. Otherwise this will try to decompress <code>len</code>
     * bytes of uncompressed data. Less than <code>len</code> bytes may
     * be read only in the following situations:
     * <ul>
     *   <li>The end of the compressed data was reached successfully.</li>
     *   <li>An error is detected after at least one but less <code>len</code>
     *       bytes have already been successfully decompressed.
     *       The next call with non-zero <code>len</code> will immediately
     *       throw the pending exception.</li>
     *   <li>An exception is thrown.</li>
     * </ul>
     *
     * @param       buf         target buffer for uncompressed data
     * @param       off         start offset in <code>buf</code>
     * @param       len         maximum number of uncompressed bytes to read
     *
     * @return      number of bytes read, or <code>-1</code> to indicate
     *              the end of the compressed stream
     *
     * @throws      CorruptedInputException
     * @throws      UnsupportedOptionsException
     * @throws      MemoryLimitException
     *
     * @throws      EOFException
     *                          compressed input is truncated or corrupt
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public int read(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length)
            throw new IllegalArgumentException();

        if (len == 0)
            return 0;

        if (exception != null)
            throw exception;

        if (endReached)
            return -1;

        int size = 0;

        try {
            while (len > 0) {
                if (blockDecoder == null) {
                    try {
                        blockDecoder = new BlockInputStream(in, check,
                                                            memoryLimit);
                    } catch (IndexIndicatorException e) {
                        indexHash.validate(in);
                        validateStreamFooter();
                        endReached = true;
                        return size > 0 ? size : -1;
                    }
                }

                int ret = blockDecoder.read(buf, off, len);

                if (ret > 0) {
                    size += ret;
                    off += ret;
                    len -= ret;
                } else if (ret == -1) {
                    indexHash.add(blockDecoder.getUnpaddedSize(),
                                  blockDecoder.getUncompressedSize());
                    blockDecoder = null;
                }
            }
        } catch (IOException e) {
            exception = e;
            if (size == 0)
                throw e;
        }

        return size;
    }

    private void validateStreamFooter() throws IOException {
        byte[] buf = new byte[DecoderUtil.STREAM_HEADER_SIZE];
        new DataInputStream(in).readFully(buf);
        StreamFlags streamFooterFlags = DecoderUtil.decodeStreamFooter(buf);

        if (!DecoderUtil.areStreamFlagsEqual(streamHeaderFlags,
                                             streamFooterFlags)
                || indexHash.getIndexSize() != streamFooterFlags.backwardSize)
            throw new CorruptedInputException(
                    "XZ Stream Footer does not match Stream Header");
    }

    /**
     * Returns the number of uncompressed bytes that can be read
     * without blocking. The value is returned with an assumption
     * that the compressed input data will be valid. If the compressed
     * data is corrupt, <code>CorruptedInputException</code> may get
     * thrown before the number of bytes claimed to be available have
     * been read from this input stream.
     *
     * @return      the number of uncompressed bytes that can be read
     *              without blocking
     */
    public int available() throws IOException {
        return blockDecoder == null ? 0 : blockDecoder.available();
    }

    /**
     * Calls <code>in.close()</code>.
     */
    public void close() throws IOException {
        in.close();
    }
}
