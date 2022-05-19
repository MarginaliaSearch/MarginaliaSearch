/*
 * LZMA2Encoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

class LZMA2Encoder extends LZMA2Coder implements FilterEncoder {
    private final LZMA2Options options;
    private final byte[] props = new byte[1];

    LZMA2Encoder(LZMA2Options options) {
        // Make a private copy so that the caller is free to change its copy.
        this.options = (LZMA2Options)options.clone();

        // TODO: Props!!!

    }

    public long getFilterID() {
        return FILTER_ID;
    }

    public byte[] getFilterProps() {
        return props;
    }

    public FinishableOutputStream getOutputStream(FinishableOutputStream out) {
        return options.getOutputStream(out);
    }
}
