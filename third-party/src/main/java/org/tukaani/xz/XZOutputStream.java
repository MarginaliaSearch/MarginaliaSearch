/*
 * XZOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.OutputStream;
import java.io.IOException;

import org.tukaani.xz.common.EncoderUtil;
import org.tukaani.xz.common.StreamFlags;
import org.tukaani.xz.check.Check;
import org.tukaani.xz.index.IndexEncoder;

/**
 * Compresses into the .xz file format.
 */
public class XZOutputStream extends FinishableOutputStream {
    private OutputStream out;
    private final StreamFlags streamFlags = new StreamFlags();
    private Check check;
    private final IndexEncoder index = new IndexEncoder();
    private FilterEncoder[] filters;
    private BlockOutputStream blockEncoder = null;
    private IOException exception = null;
    private boolean finished = false;

    /**
     * Creates a new output stream that compressed data into the .xz format.
     * This is takes options for one filter as an argument. This constructor
     * is equivalent to passing a single-member filterOptions array to the
     * other constructor.
     *
     * @param       out         output stream to which the compressed data
     *                          will be written
     *
     * @param       filterOptions
     *                          filter options to use
     *
     * @param       checkType   type of the integrity check,
     *                          for example XZ.CHECK_CRC64
     *
     * @throws      UnsupportedOptionsException
     *                          invalid filter chain
     *
     * @throws      IOException may be thrown from <code>out</code>
     */
    public XZOutputStream(OutputStream out, FilterOptions filterOptions,
                          int checkType) throws IOException {
        FilterOptions[] ops = new FilterOptions[1];
        ops[0] = filterOptions;
        initialize(out, ops, checkType);
    }

    /**
     * Creates a new output stream that compressed data into the .xz format.
     * This takes an array of filter options, allowing the caller to specify
     * a filter chain with 1-4 filters.
     *
     * @param       out         output stream to which the compressed data
     *                          will be written
     *
     * @param       filterOptions
     *                          array of filter options to use
     *
     * @param       checkType   type of the integrity check,
     *                          for example XZ.CHECK_CRC64
     *
     * @throws      UnsupportedOptionsException
     *                          invalid filter chain
     *
     * @throws      IOException may be thrown from <code>out</code>
     */
    public XZOutputStream(OutputStream out, FilterOptions[] filterOptions,
                          int checkType) throws IOException {
        initialize(out, filterOptions, checkType);
    }

    private void initialize(OutputStream out, FilterOptions[] filterOptions,
                            int checkType) throws IOException {
        this.out = out;
        updateFilters(filterOptions);

        streamFlags.checkType = checkType;
        check = Check.getInstance(checkType);

        encodeStreamHeader();
    }

    /**
     * Updates the filter chain.
     * <p>
     * Currently this cannot be used to update e.g. LZMA2 options in the
     * middle of a XZ Block. Use <code>flush()</code> to finish the current
     * XZ Block before calling this function. The new filter chain will then
     * be used for the next XZ Block.
     */
    public void updateFilters(FilterOptions[] filterOptions)
            throws XZIOException {
        if (blockEncoder != null)
            throw new UnsupportedOptionsException("Changing filter options "
                    + "in the middle of a XZ Block not implemented");

        if (filterOptions.length < 1 || filterOptions.length > 4)
            throw new UnsupportedOptionsException(
                        "XZ filter chain must be 1-4 filters");

        FilterEncoder[] newFilters = new FilterEncoder[filterOptions.length];
        for (int i = 0; i < filterOptions.length; ++i)
            newFilters[i] = filterOptions[i].getFilterEncoder();

        RawCoder.validate(newFilters);
        filters = newFilters;
    }

    /**
     * Writes one byte to be compressed.
     *
     * @throws      XZIOException
     *                          XZ stream has grown too big
     * @throws      IOException may be thrown by the underlying output stream
     */
    public void write(int b) throws IOException {
        byte[] buf = new byte[] { (byte)b };
        write(buf, 0, 1);
    }

    /**
     * Writes an array of bytes to be compressed.
     * The compressors tend to do internal buffering and thus the written
     * data won't be readable from the compressed output immediately.
     * Use <code>flush()</code> to force everything written so far to
     * be written to the underlaying output stream, but be aware that
     * flushing reduces compression ratio.
     *
     * @param       buf         buffer of bytes to be written
     * @param       off         start offset in <code>buf</code>
     * @param       len         number of bytes to write
     *
     * @throws      XZIOException
     *                          XZ stream has grown too big
     * @throws      XZIOException
     *                          <code>finish()</code> or <code>close()</code>
     *                          was already called
     * @throws      IOException may be thrown by the underlying output stream
     */
    public void write(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length)
            throw new IllegalArgumentException();

        if (len == 0)
            return;

        if (finished)
            exception = new XZIOException(
                    "XZOutputStream.write was called on a finished stream");

        if (exception != null)
            throw exception;

        if (blockEncoder == null)
            blockEncoder = new BlockOutputStream(out, filters, check);

        try {
            blockEncoder.write(buf, off, len);
        } catch (IOException e) {
            exception = e;
            throw e;
        }
    }

    /**
     * Flushes the encoder and calls <code>out.flush()</code>.
     * <p>
     * FIXME: I haven't decided yet how this will work in the final version.
     * In the current implementation, flushing finishes the current .xz Block.
     * This is equivalent to LZMA_FULL_FLUSH in liblzma (XZ Utils).
     * Equivalent of liblzma's LZMA_SYNC_FLUSH might be implemented in
     * the future, and perhaps should be what <code>flush()</code> should do.
     */
    public void flush() throws IOException {
        if (exception != null)
            throw exception;

        if (blockEncoder != null) {
            try {
                blockEncoder.finish();
                index.add(blockEncoder.getUnpaddedSize(),
                          blockEncoder.getUncompressedSize());
                blockEncoder = null;
            } catch (IOException e) {
                exception = e;
                throw e;
            }
        }

        out.flush();
    }

    /**
     * Finishes compression without closing the underlying stream.
     * No more data can be written to this stream after finishing
     * (calling <code>write</code> with an empty buffer is OK).
     * <p>
     * Repeated calls to <code>finish()</code> do nothing unless
     * an exception was thrown by this stream earlier. In that case
     * the same exception is thrown again.
     * <p>
     * After finishing, the stream may be closed normally with
     * <code>close()</code>. If the stream will be closed anyway, there
     * usually is no need to call <code>finish()</code> separately.
     */
    public void finish() throws IOException {
        if (!finished) {
            // flush() checks for pending exceptions so we don't need to
            // worry about it here.
            flush();

            try {
                index.encode(out);
                encodeStreamFooter();
                finished = true;
            } catch (IOException e) {
                exception = e;
                throw e;
            }
        }
    }

    /**
     * Finishes compression and closes the underlying stream.
     * The underlying stream <code>out</code> is closed even if finishing
     * fails. If both finishing and closing fail, the exception thrown
     * by <code>finish()</code> is thrown and the exception from the failed
     * <code>out.close()</code> is lost.
     */
    public void close() throws IOException {
        // If finish() throws an exception, it stores the exception to
        // the variable "exception". So we can ignore the possible
        // exception here.
        try {
            finish();
        } catch (IOException e) {}

        try {
            out.close();
        } catch (IOException e) {
            // Remember the exception but only if there is no previous
            // pending exception.
            if (exception == null)
                exception = e;
        }

        if (exception != null)
            throw exception;
    }

    private void encodeStreamFlags(byte[] buf, int off) {
        buf[off] = 0x00;
        buf[off + 1] = (byte)streamFlags.checkType;
    }

    private void encodeStreamHeader() throws IOException {
        out.write(XZ.HEADER_MAGIC);

        byte[] buf = new byte[2];
        encodeStreamFlags(buf, 0);
        out.write(buf);

        EncoderUtil.writeCRC32(out, buf);
    }

    private void encodeStreamFooter() throws IOException {
        byte[] buf = new byte[6];
        long backwardSize = index.getIndexSize() / 4 - 1;
        for (int i = 0; i < 4; ++i)
            buf[i] = (byte)(backwardSize >>> (i * 8));

        encodeStreamFlags(buf, 4);

        EncoderUtil.writeCRC32(out, buf);
        out.write(buf);
        out.write(XZ.FOOTER_MAGIC);
    }
}
