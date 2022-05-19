/*
 * RangeDecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.rangecoder;

import java.io.DataInputStream;
import java.io.IOException;
import org.tukaani.xz.CorruptedInputException;

public final class RangeDecoder extends RangeCoder {
    private static final int INIT_SIZE = 5;

    private final byte[] buf;
    private int pos = 0;
    private int end = 0;

    private int range = 0;
    private int code = 0;

    public RangeDecoder(int inputSizeMax) {
        buf = new byte[inputSizeMax - INIT_SIZE];
    }

    public void prepareInputBuffer(DataInputStream in, int len)
            throws IOException {
        if (len < INIT_SIZE)
            throw new CorruptedInputException();

        if (in.readUnsignedByte() != 0x00)
            throw new CorruptedInputException();

        code = in.readInt();
        range = 0xFFFFFFFF;

        pos = 0;
        end = len - INIT_SIZE;
        in.readFully(buf, 0, end);
    }

    public boolean isInBufferOK() {
        return pos <= end;
    }

    public boolean isFinished() {
        return pos == end && code == 0;
    }

    public void normalize() throws IOException {
        if ((range & TOP_MASK) == 0) {
            try {
                // If the input is corrupt, this might throw
                // ArrayIndexOutOfBoundsException.
                code = (code << SHIFT_BITS) | (buf[pos++] & 0xFF);
                range <<= SHIFT_BITS;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new CorruptedInputException();
            }
        }
    }

    public int decodeBit(short[] probs, int index) throws IOException {
        normalize();

        int prob = probs[index];
        int bound = (range >>> BIT_MODEL_TOTAL_BITS) * prob;
        int bit;

        // Compare code and bound as if they were unsigned 32-bit integers.
        if ((code ^ 0x80000000) < (bound ^ 0x80000000)) {
            range = bound;
            probs[index] = (short)(
                    prob + ((BIT_MODEL_TOTAL - prob) >>> MOVE_BITS));
            bit = 0;
        } else {
            range -= bound;
            code -= bound;
            probs[index] = (short)(prob - (prob >>> MOVE_BITS));
            bit = 1;
        }

        return bit;
    }

    public int decodeBitTree(short[] probs) throws IOException {
        int symbol = 1;

        do {
            symbol = (symbol << 1) | decodeBit(probs, symbol);
        } while (symbol < probs.length);

        return symbol - probs.length;
    }

    public int decodeReverseBitTree(short[] probs) throws IOException {
        int symbol = 1;
        int i = 0;
        int result = 0;

        do {
            int bit = decodeBit(probs, symbol);
            symbol = (symbol << 1) | bit;
            result |= bit << i++;
        } while (symbol < probs.length);

        return result;
    }

    public int decodeDirectBits(int count) throws IOException {
        int result = 0;

        do {
            normalize();

            range >>>= 1;
            int t = (code - range) >>> 31;
            code -= range & (t - 1);
            result = (result << 1) | (1 - t);
        } while (--count != 0);

        return result;
    }
}
