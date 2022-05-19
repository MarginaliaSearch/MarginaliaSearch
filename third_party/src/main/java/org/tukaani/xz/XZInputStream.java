/*
 * XZInputStream
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

/**
 * Decompresses a .xz file in streamed mode (no seeking).
 * <p>
 * Use this to decompress regular standalone .xz files. This reads from
 * its input stream until the end of the input or until an error occurs.
 * This supports decompressing concatenated .xz files.
 *
 * @see SingleXZInputStream
 */
public class XZInputStream extends InputStream {
    private final int memoryLimit;
    private final InputStream in;
    private SingleXZInputStream xzIn;
    private boolean endReached = false;
    private IOException exception = null;

    /**
     * Creates a new input stream that decompresses XZ-compressed data
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
    public XZInputStream(InputStream in) throws IOException {
        this.in = in;
        this.memoryLimit = -1;
        this.xzIn = new SingleXZInputStream(in, -1);
    }

    /**
     * Creates a new input stream that decompresses XZ-compressed data
     * from <code>in</code>.
     * <p>
     * This is identical to <code>XZInputStream(InputStream)</code> except
     * that this takes also the <code>memoryLimit</code> argument.
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
    public XZInputStream(InputStream in, int memoryLimit) throws IOException {
        this.in = in;
        this.memoryLimit = memoryLimit;
        this.xzIn = new SingleXZInputStream(in, memoryLimit);
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
                if (xzIn == null) {
                    prepareNextStream();
                    if (endReached)
                        return size == 0 ? -1 : size;
                }

                int ret = xzIn.read(buf, off, len);

                if (ret > 0) {
                    size += ret;
                    off += ret;
                    len -= ret;
                } else if (ret == -1) {
                    xzIn = null;
                }
            }
        } catch (IOException e) {
            exception = e;
            if (size == 0)
                throw e;
        }

        return size;
    }

    private void prepareNextStream() throws IOException {
        DataInputStream inData = new DataInputStream(in);
        byte[] buf = new byte[DecoderUtil.STREAM_HEADER_SIZE];

        // The size of Stream Padding must be a multiple of four bytes,
        // all bytes zero.
        do {
            // First try to read one byte to see if we have reached the end
            // of the file.
            int ret = inData.read(buf, 0, 1);
            if (ret == -1) {
                endReached = true;
                return;
            }

            // Since we got one byte of input, there must be at least
            // three more available in a valid file.
            inData.readFully(buf, 1, 3);

        } while (buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[3] == 0);

        // Not all bytes are zero. In a valid Stream it indicates the
        // beginning of the next Stream. Read the rest of the Stream Header
        // and initialize the XZ decoder.
        inData.readFully(buf, 4, DecoderUtil.STREAM_HEADER_SIZE - 4);

        try {
            xzIn = new SingleXZInputStream(in, memoryLimit, buf);
        } catch (XZFormatException e) {
            // Since this isn't the first .xz Stream, it is more
            // logical to tell that the data is corrupt.
            throw new CorruptedInputException(
                    "Garbage after a valid XZ Stream");
        }
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
        return xzIn == null ? 0 : xzIn.available();
    }

    /**
     * Calls <code>in.close()</code>.
     */
    public void close() throws IOException {
        in.close();
    }
}
