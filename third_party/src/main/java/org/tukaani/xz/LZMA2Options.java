/*
 * LZMA2Options
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;
import java.io.IOException;

/**
 * Options for LZMA2.
 * <p>
 * FIXME: This is unfinished and things might change.
 */
public class LZMA2Options extends FilterOptions {
    /**
     * Default compression preset.
     */
    public static final int PRESET_DEFAULT = 6;

    /**
     * Minimum dictionary size.
     */
    public static final int DICT_SIZE_MIN = 4096;

    /**
     * Maximum dictionary size for compression.
     * <p>
     * FIXME? Decompression dictionary size can be bigger.
     */
    public static final int DICT_SIZE_MAX = 128 << 20;

    /**
     * Maximum value for lc + lp.
     */
    public static final int LC_LP_MAX = 4;

    /**
     * Maximum value for pb.
     */
    public static final int PB_MAX = 4;

    /**
     * Compression mode: uncompressed.
     * The data is wrapped into a LZMA2 stream without compression.
     */
    public static final int MODE_UNCOMPRESSED = 0;

    /**
     * Compression mode: fast.
     * This is usually combined with a hash chain match finder.
     */
    public static final int MODE_FAST = 1;

    /**
     * Compression mode: normal.
     * This is usually combined with a binary tree match finder.
     */
    public static final int MODE_NORMAL = 2;

    /**
     * Minimum value for <code>niceLen</code>.
     */
    public static final int NICE_LEN_MIN = 8;

    /**
     * Maximum value for <code>niceLen</code>.
     */
    public static final int NICE_LEN_MAX = 273;

    /**
     * Match finder: Hash Chain 2-3-4
     */
    public static final int MF_HC4 = 0x04;

    /**
     * Match finder: Binary tree 2-3-4
     */
    public static final int MF_BT4 = 0x14;

    private int dictSize;

/*
    public int lc;
    public int lp;
    public int pb;
    public int mode;
    public int niceLen;
    public int mf;
    public int depth;
*/

    public LZMA2Options() {
        setPreset(PRESET_DEFAULT);
    }

    public LZMA2Options(int preset) {
        setPreset(preset);
    }

    public void setPreset(int preset) {
        // TODO
        dictSize = 8 << 20;
    }

    public int getEncoderMemoryUsage() {
        return LZMA2OutputStream.getMemoryUsage(this);
    }

    public FinishableOutputStream getOutputStream(FinishableOutputStream out) {
        return new LZMA2OutputStream(out, this);
    }

    public int getDecoderMemoryUsage() {
        return LZMA2InputStream.getMemoryUsage(dictSize);
    }

    public InputStream getInputStream(InputStream in) throws IOException {
        return new LZMA2InputStream(in, dictSize);
    }

    FilterEncoder getFilterEncoder() {
        return new LZMA2Encoder(this);
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // Never reached
            throw new RuntimeException();
        }
    }
}
